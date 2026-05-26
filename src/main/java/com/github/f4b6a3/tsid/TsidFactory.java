/*
 * MIT License
 *
 * Copyright (c) 2020-2022 Fabio Lima
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.f4b6a3.tsid;

import static com.github.f4b6a3.tsid.Tsid.RANDOM_BITS;
import static com.github.f4b6a3.tsid.Tsid.RANDOM_MASK;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * A factory that actually generates Time-Sorted Unique Identifiers (TSID).
 * <p>
 * This factory is used by the {@link TsidCreator} utility.
 * <p>
 * Most people just need {@link TsidCreator}. However, you can use this class if
 * you need to make some customizations, for example changing the default
 * {@link SecureRandom} random generator to a faster pseudo-random generator.
 * <p>
 * If a system property "tsidcreator.node" or environment variable
 * "TSIDCREATOR_NODE" is defined, its value is utilized as node identifier. One
 * of them <b>should</b> be defined to embed a machine ID in the generated TSID
 * in order to avoid TSID collisions. Using that property or variable is
 * <b>highly recommended</b>. If no property or variable is defined, a random
 * node ID is generated at initialization.
 * <p>
 * If a system property "tsidcreator.node.count" or environment variable
 * "TSIDCREATOR_NODE_COUNT" is defined, its value is utilized by the
 * constructors of this class to adjust the amount of bits needed to embed the
 * node ID. For example, if the number 50 is given, the node bit amount is
 * adjusted to 6, which is the minimum number of bits to accommodate 50 nodes.
 * If no property or variable is defined, the number of bits reserved for node
 * ID is set to 10, which can accommodate 1024 nodes.
 * <p>
 * A "node" as we call it in this library can be a physical machine, a virtual
 * machine, a container, a k8s pod, a running process, etc.
 * <p>
 * This class <b>should</b> be used as a singleton. Make sure that you create
 * and reuse a single instance of {@link TsidFactory} per node in your
 * distributed system.
 */
public final class TsidFactory {

    private int counter;

    private long lastTime;

    private final int node;

    private final int nodeBits;

    private final int counterBits;

    private final int nodeMask;

    private final int counterMask;

    private final long customEpoch;

    private final LongSupplier timeFunction;

    private final IRandom random;

    private final int randomBytes;

    private final ReentrantLock lock = new ReentrantLock();

    static final int NODE_BITS_256 = 8;

    static final int NODE_BITS_1024 = 10;

    static final int NODE_BITS_4096 = 12;

    // ******************************
    // Constructors
    // ******************************
    /**
     * It builds a new factory.
     * <p>
     * The node identifier provided by the "tsidcreator.node" system property or the
     * "TSIDCREATOR_NODE" environment variable is embedded in the generated TSIDs in
     * order to avoid collisions. It is <b>highly recommended</b> defining that
     * property or variable. Otherwise the node identifier will be randomly chosen.
     * <p>
     * If a system property "tsidcreator.node.count" or environment variable
     * "TSIDCREATOR_NODE_COUNT" is defined, its value is used to adjust the node
     * bits amount.
     */
    public TsidFactory() {
        this(builder());
    }

    /**
     * It builds a new factory.
     * <p>
     * The node identifier provided by parameter is embedded in the generated TSIDs
     * in order to avoid collisions.
     * <p>
     * If a system property "tsidcreator.node.count" or environment variable
     * "TSIDCREATOR_NODE_COUNT" is defined, its value is used to adjust the node
     * bits amount.
     *
     * @param node the node identifier
     */
    public TsidFactory(int node) {
        this(builder().withNode(node));
    }

    /**
     * It builds a generator with the given builder.
     *
     * @param builder a builder instance
     */
    private TsidFactory(Builder builder) {
        // setup the custom epoch, the node bits, etc
        this.customEpoch = builder.getCustomEpoch();
        this.nodeBits = builder.getNodeBits();
        this.random = builder.getRandom();
        this.timeFunction = builder.getTimeFunction();
        // setup constants that depend on node bits
        this.counterBits = RANDOM_BITS - nodeBits;
        this.counterMask = RANDOM_MASK >>> nodeBits;
        this.nodeMask = RANDOM_MASK >>> counterBits;
        // setup how many bytes to get from the random function
        this.randomBytes = ((this.counterBits - 1) / 8) + 1;
        // setup the node identifier
        this.node = builder.getNode() & nodeMask;
        // finally initialize inner state
        // 1970-01-01
        this.lastTime = 0L;
        this.counter = getRandomCounter();
    }

    /**
     * Returns a new factory for up to 256 nodes and 16384 ID/ms.
     *
     * @return {@link TsidFactory}
     */
    public static TsidFactory newInstance256() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns a new factory for up to 256 nodes and 16384 ID/ms.
     *
     * @param node the node identifier
     * @return {@link TsidFactory}
     */
    public static TsidFactory newInstance256(int node) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns a new factory for up to 1024 nodes and 4096 ID/ms.
     *
     * It is equivalent to {@code new TsidFactory()}.
     *
     * @return {@link TsidFactory}
     */
    public static TsidFactory newInstance1024() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns a new factory for up to 1024 nodes and 4096 ID/ms.
     *
     * It is equivalent to {@code new TsidFactory(int)}.
     *
     * @param node the node identifier
     * @return {@link TsidFactory}
     */
    public static TsidFactory newInstance1024(int node) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns a new factory for up to 4096 nodes and 1024 ID/ms.
     *
     * @return {@link TsidFactory}
     */
    public static TsidFactory newInstance4096() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns a new factory for up to 4096 nodes and 1024 ID/ms.
     *
     * @param node the node identifier
     * @return {@link TsidFactory}
     */
    public static TsidFactory newInstance4096(int node) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    // ******************************
    // Public methods
    // ******************************
    /**
     * Returns a TSID.
     *
     * @return a TSID.
     */
    public Tsid create() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns the current time.
     * <p>
     * If the current time is equal to the previous time, the counter is incremented
     * by one. Otherwise the counter is reset to a random value.
     * <p>
     * The maximum number of increment operations depend on the counter bits. For
     * example, if the counter bits is 12, the maximum number of increment
     * operations is 2^12 = 4096.
     *
     * @return the current time
     */
    private long getTime() {
        long time = timeFunction.getAsLong();
        if (time <= this.lastTime) {
            this.counter++;
            // Carry is 1 if an overflow occurs after ++.
            int carry = this.counter >>> this.counterBits;
            this.counter = this.counter & this.counterMask;
            // increment time
            time = this.lastTime + carry;
        } else {
            // If the system clock has advanced as expected,
            // simply reset the counter to a new random value.
            this.counter = this.getRandomCounter();
        }
        // save current time
        this.lastTime = time;
        // adjust to the custom epoch
        return time - this.customEpoch;
    }

    /**
     * Returns a random counter value from 0 to 0x3fffff (2^22-1 = 4,194,303).
     * <p>
     * The counter maximum value depends on the node identifier bits. For example,
     * if the node identifier has 10 bits, the counter has 12 bits.
     *
     * @return a number
     */
    private int getRandomCounter() {
        if (random instanceof ByteRandom) {
            final byte[] bytes = random.nextBytes(this.randomBytes);
            switch(bytes.length) {
                case 1:
                    return (bytes[0] & 0xff) & this.counterMask;
                case 2:
                    return (((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff)) & this.counterMask;
                default:
                    return (((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff)) & this.counterMask;
            }
        } else {
            return random.nextInt() & this.counterMask;
        }
    }

    /**
     * Returns a builder object.
     * <p>
     * It is used to build a custom {@link TsidFactory}.
     *
     * @return a builder
     */
    public static Builder builder() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    // ******************************
    // Package-private inner classes
    // ******************************
    /**
     * A nested class that builds custom TSID factories.
     * <p>
     * It is used to setup a custom {@link TsidFactory}.
     */
    public static class Builder {

        private Integer node;

        private Integer nodeBits;

        private Long customEpoch;

        private IRandom random;

        private LongSupplier timeFunction;

        /**
         * Set the node identifier.
         *
         * @param node a number that must be between 0 and 2^nodeBits-1.
         * @return {@link Builder}
         * @throws IllegalArgumentException if the node identifier is out of the range
         *                                  [0, 2^nodeBits-1] when {@code build()} is
         *                                  invoked
         */
        public Builder withNode(Integer node) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Set the node identifier bits length.
         *
         * @param nodeBits a number that must be between 0 and 20.
         * @return {@link Builder}
         * @throws IllegalArgumentException if the node bits are out of the range [0,
         *                                  20] when {@code build()} is invoked
         */
        public Builder withNodeBits(Integer nodeBits) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Set the custom epoch.
         *
         * @param customEpoch an instant that represents the custom epoch.
         * @return {@link Builder}
         */
        public Builder withCustomEpoch(Instant customEpoch) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Set the random generator.
         * <p>
         * The random generator is used to create a random function that is used to
         * reset the counter when the millisecond changes.
         *
         * @param random a {@link Random} generator
         * @return {@link Builder}
         */
        public Builder withRandom(Random random) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Set the random function.
         * <p>
         * The random function is used to reset the counter when the millisecond
         * changes.
         *
         * @param randomFunction a random function that returns a integer value
         * @return {@link Builder}
         */
        public Builder withRandomFunction(IntSupplier randomFunction) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Set the random function.
         * <p>
         * The random function must return a byte array of a given length.
         * <p>
         * The random function is used to reset the counter when the millisecond
         * changes.
         * <p>
         * Despite its name, the random function MAY return a fixed value, for example,
         * if your app requires the counter to be reset to ZERO whenever the millisecond
         * changes, like Twitter Snowflakes, this function should return an array filled
         * with ZEROS.
         *
         * @param randomFunction a random function that returns a byte array
         * @return {@link Builder}
         */
        public Builder withRandomFunction(IntFunction<byte[]> randomFunction) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Set the clock to be used in tests.
         * <p>
         * {@link Clock} is too complicated. Use {@link #withTimeFunction(LongSupplier)}
         * instead.
         *
         * @param clock a clock
         * @return {@link Builder}
         */
        public Builder withClock(Clock clock) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Set the time function to be used in tests.
         *
         * @param timeFunction a function that returns the current time in milliseconds,
         *                     measured from the UNIX epoch of 1970-01-01T00:00Z (UTC)
         * @return {@link Builder}
         */
        public Builder withTimeFunction(LongSupplier timeFunction) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Get the node identifier.
         *
         * @return a number
         * @throws IllegalArgumentException if the node is out of range
         */
        protected Integer getNode() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Get the node identifier bits length within the range 0 to 20.
         *
         * @return a number
         * @throws IllegalArgumentException if the node bits are out of range
         */
        protected Integer getNodeBits() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Gets the custom epoch.
         *
         * @return a number
         */
        protected Long getCustomEpoch() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Gets the random generator.
         *
         * @return a random generator
         */
        protected IRandom getRandom() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Gets the time function to be used in tests.
         *
         * @return a time function
         */
        protected LongSupplier getTimeFunction() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        /**
         * Returns a build TSID factory.
         *
         * @return {@link TsidFactory}
         * @throws IllegalArgumentException if the node is out of range
         * @throws IllegalArgumentException if the node bits are out of range
         */
        public TsidFactory build() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }
    }

    static interface IRandom {

        public int nextInt();

        public byte[] nextBytes(int length);
    }

    static class IntRandom implements IRandom {

        private final IntSupplier randomFunction;

        public IntRandom() {
            this(newRandomFunction(null));
        }

        public IntRandom(Random random) {
            this(newRandomFunction(random));
        }

        public IntRandom(IntSupplier randomFunction) {
            this.randomFunction = randomFunction != null ? randomFunction : newRandomFunction(null);
        }

        @Override
        public int nextInt() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        @Override
        public byte[] nextBytes(int length) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        protected static IntSupplier newRandomFunction(Random random) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }
    }

    static class ByteRandom implements IRandom {

        private final IntFunction<byte[]> randomFunction;

        public ByteRandom() {
            this(newRandomFunction(null));
        }

        public ByteRandom(Random random) {
            this(newRandomFunction(random));
        }

        public ByteRandom(IntFunction<byte[]> randomFunction) {
            this.randomFunction = randomFunction != null ? randomFunction : newRandomFunction(null);
        }

        @Override
        public int nextInt() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        @Override
        public byte[] nextBytes(int length) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        protected static IntFunction<byte[]> newRandomFunction(Random random) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }
    }

    static class Settings {

        static final String NODE = "tsidcreator.node";

        static final String NODE_COUNT = "tsidcreator.node.count";

        private Settings() {
        }

        public static Integer getNode() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        public static Integer getNodeCount() {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        static Integer getPropertyAsInteger(String property) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }

        static String getProperty(String name) {
            throw new UnsupportedOperationException("STUB: not implemented");
        }
    }
}
