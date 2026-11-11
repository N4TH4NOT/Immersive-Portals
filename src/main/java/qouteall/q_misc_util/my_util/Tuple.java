package qouteall.q_misc_util.my_util;

/** Immutable pair used by the geometry API. */
public record Tuple<A, B>(A a, B b) {
    public A getA() {
        return a;
    }

    public B getB() {
        return b;
    }
}
