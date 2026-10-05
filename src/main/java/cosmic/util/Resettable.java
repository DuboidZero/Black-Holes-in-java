package cosmic.util;

/**
 * Interface representing an object whose state can be reset to default values.
 *
 * <p>Demonstrates polymorphism and clean component life-cycle contracts.</p>
 */
public interface Resettable {

    /**
     * Resets internal state to default configured parameters.
     */
    void reset();
}
