import os, sys, re, ctypes, numpy as np
os.environ["PYOPENGL_PLATFORM"] = "egl"
from OpenGL import EGL
from OpenGL import GLES2 as gl
from PIL import Image
W,H = int(sys.argv[2]), int(sys.argv[3])
dpy = EGL.eglGetDisplay(EGL.EGL_DEFAULT_DISPLAY); EGL.eglInitialize(dpy, None, None)
attrs = (EGL.EGLint * 7)(EGL.EGL_SURFACE_TYPE, EGL.EGL_PBUFFER_BIT, EGL.EGL_RENDERABLE_TYPE, EGL.EGL_OPENGL_ES2_BIT, EGL.EGL_NONE, 0, 0)
cfg = EGL.EGLConfig(); n = EGL.EGLint()
EGL.eglChooseConfig(dpy, attrs, ctypes.pointer(cfg), 1, ctypes.pointer(n)); EGL.eglBindAPI(EGL.EGL_OPENGL_ES_API)
surf = EGL.eglCreatePbufferSurface(dpy, cfg, (EGL.EGLint * 5)(EGL.EGL_WIDTH, W, EGL.EGL_HEIGHT, H, EGL.EGL_NONE))
ctx = EGL.eglCreateContext(dpy, cfg, EGL.EGL_NO_CONTEXT, (EGL.EGLint * 3)(EGL.EGL_CONTEXT_CLIENT_VERSION, 2, EGL.EGL_NONE))
EGL.eglMakeCurrent(dpy, surf, surf, ctx)
rs = open(sys.argv[1]).read()
src = re.search(r'pub const GAME_FRAG: &str = r#"(.*?)"#;', rs, re.S).group(1)
VERT = "attribute vec2 a_pos; varying vec2 v_uv; void main(){ v_uv = vec2(a_pos.x, 1.0-a_pos.y); gl_Position = vec4(a_pos*2.0-1.0,0.0,1.0);} "
def prog(mode):
    def sh(kind, s):
        x = gl.glCreateShader(kind); gl.glShaderSource(x, s); gl.glCompileShader(x)
        assert gl.glGetShaderiv(x, gl.GL_COMPILE_STATUS), gl.glGetShaderInfoLog(x); return x
    p = gl.glCreateProgram()
    gl.glAttachShader(p, sh(gl.GL_VERTEX_SHADER, VERT))
    gl.glAttachShader(p, sh(gl.GL_FRAGMENT_SHADER, "#define FRAG_COLOR gl_FragColor\n#define LCD_FX 0\n" + src.replace("uniform float u_mode;", "const float u_mode = %.1f;" % mode)))
    gl.glBindAttribLocation(p, 0, "a_pos"); gl.glLinkProgram(p); return p
base = Image.open(sys.argv[4]).convert("RGB").resize((320,240), Image.BICUBIC)
def tex(img):
    t = gl.glGenTextures(1); gl.glBindTexture(gl.GL_TEXTURE_2D, t)
    for k,v in ((gl.GL_TEXTURE_MIN_FILTER,gl.GL_NEAREST),(gl.GL_TEXTURE_MAG_FILTER,gl.GL_NEAREST),(gl.GL_TEXTURE_WRAP_S,gl.GL_CLAMP_TO_EDGE),(gl.GL_TEXTURE_WRAP_T,gl.GL_CLAMP_TO_EDGE)):
        gl.glTexParameteri(gl.GL_TEXTURE_2D, k, v)
    a = np.array(img.convert("RGBA"), dtype=np.uint8)
    gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA, img.width, img.height, 0, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, a)
    return t
quad = np.array([0,0, 1,0, 0,1, 1,1], dtype=np.float32)
out=[]
names={13:"zfast-crt",14:"crt-pi",15:"GDV Trinitron",16:"CRT Geom Deluxe",17:"fakelottes",18:"crt-aperture",20:"crt-easymode",21:"crt-hyllian"}
for mode in names:
    row=[]
    for img in (base, base.resize((640,480), Image.NEAREST)):
        p = prog(mode); gl.glUseProgram(p); t = tex(img)
        u = lambda n: gl.glGetUniformLocation(p, n)
        gl.glUniform1i(u("u_game"), 0); gl.glUniform2f(u("u_src"), img.width, img.height)
        gl.glUniform4f(u("u_uv"), 0,0,1,1); gl.glUniform1f(u("u_bright"), 1.0); gl.glUniform1f(u("u_colour_correct"), 0.0)
        gl.glUniform4f(u("u_rect"), 0,0,W,H); gl.glUniform2f(u("u_fbo"), 1,1); gl.glUniform4f(u("u_pic"), 0,0,1,1)
        gl.glViewport(0,0,W,H); gl.glClear(gl.GL_COLOR_BUFFER_BIT)
        gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0,2,gl.GL_FLOAT,False,0,quad)
        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP,0,4)
        buf = np.zeros((H,W,4), dtype=np.uint8); gl.glReadPixels(0,0,W,H,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,buf); px = buf.tobytes()
        im = Image.frombytes("RGBA",(W,H),px).transpose(Image.FLIP_TOP_BOTTOM).convert("RGB")
        row.append(im.crop((W//2-150, H//2-110, W//2+150, H//2+110)))
    out.append((names[mode],row))
from PIL import ImageDraw
sheet = Image.new("RGB",(620, len(out)*240),"white")
for i,(n,(a,b)) in enumerate(out):
    sheet.paste(a,(0,i*240+20)); sheet.paste(b,(320,i*240+20))
    ImageDraw.Draw(sheet).text((5,i*240+3), n+"  (left 240 lines, right same picture as 480 lines)", fill=(0,0,0))
sheet.save(sys.argv[5])
