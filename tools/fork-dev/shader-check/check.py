import os, sys, re, ctypes
os.environ["PYOPENGL_PLATFORM"] = "egl"
from OpenGL import EGL
from OpenGL import GLES2 as gl
dpy = EGL.eglGetDisplay(EGL.EGL_DEFAULT_DISPLAY)
major, minor = EGL.EGLint(), EGL.EGLint()
assert EGL.eglInitialize(dpy, ctypes.pointer(major), ctypes.pointer(minor))
attrs = (EGL.EGLint * 7)(EGL.EGL_SURFACE_TYPE, EGL.EGL_PBUFFER_BIT, EGL.EGL_RENDERABLE_TYPE, EGL.EGL_OPENGL_ES2_BIT, EGL.EGL_NONE, 0, 0)
cfg = EGL.EGLConfig(); n = EGL.EGLint()
assert EGL.eglChooseConfig(dpy, attrs, ctypes.pointer(cfg), 1, ctypes.pointer(n)) and n.value
EGL.eglBindAPI(EGL.EGL_OPENGL_ES_API)
surf = EGL.eglCreatePbufferSurface(dpy, cfg, (EGL.EGLint * 5)(EGL.EGL_WIDTH, 16, EGL.EGL_HEIGHT, 16, EGL.EGL_NONE))
ctx = EGL.eglCreateContext(dpy, cfg, EGL.EGL_NO_CONTEXT, (EGL.EGLint * 3)(EGL.EGL_CONTEXT_CLIENT_VERSION, 2, EGL.EGL_NONE))
assert EGL.eglMakeCurrent(dpy, surf, surf, ctx)
print("GL", gl.glGetString(gl.GL_VERSION))
def compile(src, kind=gl.GL_FRAGMENT_SHADER):
    sh = gl.glCreateShader(kind); gl.glShaderSource(sh, src); gl.glCompileShader(sh)
    ok = gl.glGetShaderiv(sh, gl.GL_COMPILE_STATUS)
    return ok, gl.glGetShaderInfoLog(sh)
rs = open(sys.argv[1]).read()
src = re.search(r'pub const GAME_FRAG: &str = r#"(.*?)"#;', rs, re.S).group(1)
for mode in range(0, 26):
    for lcd in (0,):
        full = "#define FRAG_COLOR gl_FragColor\n#define LCD_FX %d\n" % lcd + src.replace("uniform float u_mode;", "const float u_mode = %.1f;" % mode)
        ok, log = compile(full)
        print(mode, "OK" if ok else "FAIL", ("\n".join(l for l in log.decode().splitlines() if "error" in l)[:600] if log and not ok else ""))
