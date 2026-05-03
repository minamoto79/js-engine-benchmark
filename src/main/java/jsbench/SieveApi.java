package jsbench;

import io.roastedroot.quickjs4j.annotations.GuestFunction;
import io.roastedroot.quickjs4j.annotations.Invokables;

/**
 * Typed guest interface for QuickJs4J. The annotation processor generates
 * `SieveApi_Invokables` next to this interface at `mvn compile`. Kept in Java
 * because Kotlin compile runs before the Java annotation processor and can't
 * see the generated class — putting both interface and consumer (the backend
 * that uses the generated class) on the Java side avoids that ordering.
 */
@Invokables("bench")
public interface SieveApi {
    @GuestFunction
    int sieve(int n);
}
