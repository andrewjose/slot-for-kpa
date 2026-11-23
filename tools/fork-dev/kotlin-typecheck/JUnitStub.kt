package org.junit
annotation class Test
object Assert { @JvmStatic fun assertEquals(a: Any?, b: Any?) { check(a == b) { "expected <$a> got <$b>" } }; @JvmStatic fun assertTrue(b: Boolean) { check(b) }; @JvmStatic fun assertNull(a: Any?) { check(a == null) { "expected null got $a" } } }
