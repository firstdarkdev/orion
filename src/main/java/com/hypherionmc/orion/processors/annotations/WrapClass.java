package com.hypherionmc.orion.processors.annotations;

import java.lang.annotation.*;

/**
 * @author HypherionSA
 *
 * An extension system for Nojang, that allows wrapping Minecraft Classes into a custom API,
 * without needing to write wrap/unwrap methods. This is totally unneeded, but I am a lazy fuck
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface WrapClass {
    Class<?> value();
}
