package com.buschmais.sarf.framework;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Stephan Pirnbaum
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Materializable {
    Class<? extends SARFDescriptor> value() default SARFDescriptor.class;
}
