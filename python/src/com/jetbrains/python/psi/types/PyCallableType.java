package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyQualifiedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A type instances of which can possibly be called. For example, a class definition can be called, and the result of a call is a class
 * instance.
 *
 * @author yole
 */
public interface PyCallableType extends PyType {
  /**
   * Returns the type which is the result of calling an instance of this type.
   *
   * @return the call result type or null if invalid.
   * @param context
   * @param callSite
   */
  @Nullable
  PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite);
}
