package com.clearfolio.viewer.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class StringUtilsTest {

    @Test
    public void testRemoveNullChars() {
        assertNull(StringUtils.removeNullChars(null));
        assertEquals("", StringUtils.removeNullChars(""));
        assertEquals("abc", StringUtils.removeNullChars("abc"));
        assertEquals("abc", StringUtils.removeNullChars("a\u0000bc"));
        assertEquals("abc", StringUtils.removeNullChars("\u0000a\u0000b\u0000c\u0000"));
    }

    @Test
    public void testPrivateConstructor() throws Exception {
        Constructor<StringUtils> constructor = StringUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException ex) {
            // expected
        }
    }
}
