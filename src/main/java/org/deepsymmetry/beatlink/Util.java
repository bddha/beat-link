package org.deepsymmetry.beatlink;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides utility functions.
 *
 * @author James Elliott
 */
@SuppressWarnings("WeakerAccess")
public class Util {

    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    /**
     * The sequence of ten bytes which begins all UDP packets sent in the protocol.
     */
    private static final byte[] MAGIC_HEADER = {0x51, 0x73, 0x70, 0x74, 0x31, 0x57, 0x6d, 0x4a, 0x4f, 0x4c};

    /**
     * Get the sequence of nine bytes which begins all UDP packets sent in the protocol as a {@link ByteBuffer}.
     * Each call returns a new instance, so you don't need to worry about messing with buffer positions.
     *
     * @return a read-only {@link ByteBuffer} containing the header with which all protocol packets begin.
     */
    public static ByteBuffer getMagicHeader() {
        return ByteBuffer.wrap(MAGIC_HEADER).asReadOnlyBuffer();
    }

    /**
     * The offset into protocol packets which identify the content of the packet.
     */
    public static final int PACKET_TYPE_OFFSET = 0x0a;

    /**
     * The known packet types used in the protocol, along with the byte values
     * which identify them, and the names by which we describe them, and the port
     * on which they are received.
     */
    public enum PacketType {

        /**
         * Used by the mixer to tell a set of players to start and/or stop playing.
         */
        FADER_START_COMMAND(0x02, "Fader Start", BeatFinder.BEAT_PORT),

        /**
         * Used by the mixer to tell the players which channels are on and off the air.
         */
        CHANNELS_ON_AIR(0x03, "Channels On Air", BeatFinder.BEAT_PORT),

        /**
         * Used to ask a player for information about the media mounted in a slot.
         */
        MEDIA_QUERY(0x05, "Media Query", VirtualCdj.UPDATE_PORT),

        /**
         * The response sent when a Media Query is received.
         */
        MEDIA_RESPONSE(0x06, "Media Response", VirtualCdj.UPDATE_PORT),

        /**
         * An initial series of three of these packets is sent when a device first joins the network, at 300ms
         * intervals.
         */
        DEVICE_HELLO(0x0a, "Device Hello", DeviceFinder.ANNOUNCEMENT_PORT),

        /**
         * A series of three of these is sent at 300ms intervals when a device is starting to establish its
         * device number.
         */
        DEVICE_NUMBER_STAGE_1(0x00, "Device Number Claim Stage 1", DeviceFinder.ANNOUNCEMENT_PORT),

        /**
         * This packet is sent by a mixer directly to a device which has just sent a device number self-assignment
         * packet when that device is plugged into a channel-specific Ethernet jack on the mixer (or XDJ-XZ) to let
         * the device know the sender of this packet is responsible for assigning its number.
         */
        DEVICE_NUMBER_WILL_ASSIGN(0x01, "Device Number Will Be Assigned", DeviceFinder.ANNOUNCEMENT_PORT),

        /**
         * A second series of three packets sent at 300ms intervals when the device is claiming its device number.
         */
        DEVICE_NUMBER_STAGE_2(0x02, "Device Number Claim Stage 2", DeviceFinder.ANNOUNCEMENT_PORT),

        /**
         * This packet is sent by a mixer (or XDJ-XZ) when a player has acknowledged that it is ready to be assigned
         * the device number that belongs to the jack to which it is connected.
         */
        DEVICE_NUMBER_ASSIGN(0x03, "Device Number Assignment", DeviceFinder.ANNOUNCEMENT_PORT),

        /**
         * Third and final series of three packets sent at 300ms intervals when a device is claiming its device number.
         * If the device is configured to use a specific number, only one is sent.
         */
        DEVICE_NUMBER_STAGE_3(0x04, "Device Number Claim Stage 3", DeviceFinder.ANNOUNCEMENT_PORT),

        /**
         * This packet is sent by a mixer (or XDJ-XZ) once it sees that device number assignment has concluded
         * successfully, to the player plugged into a channel-specific jack.
         */
        DEVICE_NUMBER_ASSIGNMENT_FINISHED(0x05, "Device Number Assignment Finished", DeviceFinder.ANNOUNCEMENT_PORT),

        /**
         * Used to report that a device is still present on the DJ Link network.
         */
        DEVICE_KEEP_ALIVE(0x06, "Device Keep-Alive", DeviceFinder.ANNOUNCEMENT_PORT),

        /**
         * Used to defend a device number that is already in use.
         */
        DEVICE_NUMBER_IN_USE(0x08, "Device Number In Use", DeviceFinder.ANNOUNCEMENT_PORT),

        /**
         * A status update from a player, with a great many status flags, pitch, tempo, and beat-within-bar details.
         * Sadly, the same number is used (on port 50000) as part of the CDJ startup process.
         */
        CDJ_STATUS(0x0a, "CDJ Status", VirtualCdj.UPDATE_PORT),

        /**
         * A command to load a particular track; usually sent by rekordbox.
         */
        LOAD_TRACK_COMMAND(0x19, "Load Track Command", VirtualCdj.UPDATE_PORT),

        /**
         * A response indicating that the specified track is being loaded.
         */
        LOAD_TRACK_ACK(0x1a, "Load Track Acknowledgment", VirtualCdj.UPDATE_PORT),

        /**
         * Used by an incoming tempo master to ask the current tempo master to relinquish that role.
         */
        MASTER_HANDOFF_REQUEST(0x26, "Master Handoff Request", BeatFinder.BEAT_PORT),

        /**
         * Used by the active tempo master to respond to a request to relinquish that role.
         */
        MASTER_HANDOFF_RESPONSE(0x27, "Master Handoff Response", BeatFinder.BEAT_PORT),

        /**
         * Announces a beat has been played in a rekordbox-analyzed track, with lots of useful synchronization
         * information.
         */
        BEAT(0x28, "Beat", BeatFinder.BEAT_PORT),

        /**
         * A status update from the mixer, with status flags, pitch, and tempo, and beat-within-bar information.
         */
        MIXER_STATUS(0x29, "Mixer Status", VirtualCdj.UPDATE_PORT),

        /**
         * Used to tell a player to turn sync on or off, or that it should become the tempo master.
         */
        SYNC_CONTROL(0x2a, "Sync Control", BeatFinder.BEAT_PORT);

        /**
         * The value that appears in the type byte which identifies this type of packet.
         */
        public final byte protocolValue;

        /**
         * The name by which we describe this kind of packet.
         */
        public final String name;

        /**
         * The port on which this kind of packet is received.
         */
        public final int port;

        /**
         * Constructor simply sets the protocol value and name.
         *
         * @param value the value that appears in the type byte which identifies this type of packet
         * @param name how we describe this kind of packet
         * @param port the port number on which this kind of packet is received
         */
        PacketType(int value, String name, int port) {
            protocolValue = (byte)value;
            this.name = name;
            this.port = port;
        }
    }

    /**
     * Allows a known packet type to be looked up given the port number it was received on and the packet type byte.
     */
    public static final Map<Integer, Map<Byte, PacketType>> PACKET_TYPE_MAP;
    static {
        Map<Integer, Map<Byte, PacketType>> scratch = new HashMap<Integer, Map<Byte, PacketType>>();
        for (PacketType packetType : PacketType.values()) {
            Map<Byte, PacketType> portMap = scratch.get(packetType.port);
            if (portMap == null) {
                portMap = new HashMap<Byte, PacketType>();
                scratch.put(packetType.port, portMap);
            }
            portMap.put(packetType.protocolValue, packetType);
        }
        for (Map.Entry<Integer, Map<Byte, PacketType>> entry : scratch.entrySet()) {
            scratch.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        PACKET_TYPE_MAP = Collections.unmodifiableMap(scratch);
    }

    /**
     * Build a standard-format UDP packet for sending to port 50001 or 50002 in the protocol.
     *
     * @param type the type of packet to create.
     * @param deviceName the 0x14 (twenty) bytes of the device name to send in the packet.
     * @param payload the remaining bytes which come after the device name.
     * @return the packet to send.
     */
    public static DatagramPacket buildPacket(PacketType type, ByteBuffer deviceName, ByteBuffer payload) {
        ByteBuffer content = ByteBuffer.allocate(0x1f + payload.remaining());
        content.put(getMagicHeader());
        content.put(type.protocolValue);
        content.put(deviceName);
        content.put(payload);
        return new DatagramPacket(content.array(), content.capacity());
    }

    /**
     * Check to see whether a packet starts with the standard header bytes, followed by a known byte identifying it.
     * If so, return the kind of packet that has been recognized.
     *
     * @param packet a packet that has just been received
     * @param port the port on which the packet has been received
     *
     * @return the type of packet that was recognized, or {@code null} if the packet was not recognized
     */
    public static PacketType validateHeader(DatagramPacket packet, int port) {
        byte[] data = packet.getData();

        if (data.length < PACKET_TYPE_OFFSET) {
            logger.warn("Packet is too short to be a Pro DJ Link packet; must be at least " + PACKET_TYPE_OFFSET +
                    " bytes long, was only " + data.length + ".");
            return null;
        }

        if (!getMagicHeader().equals(ByteBuffer.wrap(data, 0, MAGIC_HEADER.length))) {
            logger.warn("Packet did not have correct ten-byte header for the Pro DJ Link protocol.");
            return null;
        }

        final Map<Byte, PacketType> portMap = PACKET_TYPE_MAP.get(port);
        if (portMap == null) {
            logger.warn("Do not know any Pro DJ Link packets that are received on port " + port + ".");
            return null;
        }

        final PacketType result = portMap.get(data[PACKET_TYPE_OFFSET]);
        if (result == null) {
            logger.warn("Do not know any Pro DJ Link packets received on port " + port + " with type " +
                    String.format("0x%02x", data[PACKET_TYPE_OFFSET]) + ".");
        }

        return result;
    }

    /**
     * Converts a signed byte to its unsigned int equivalent in the range 0-255.
     *
     * @param b a byte value to be considered an unsigned integer
     *
     * @return the unsigned version of the byte
     */
    public static int unsign(byte b) {
        return b & 0xff;
    }

    /**
     * Reconstructs a number that is represented by more than one byte in a network packet in big-endian order.
     *
     * @param buffer the byte array containing the packet data
     * @param start the index of the first byte containing a numeric value
     * @param length the number of bytes making up the value
     * @return the reconstructed number
     */
    public static long bytesToNumber(byte[] buffer, int start, int length) {
        long result = 0;
        for (int index = start; index < start + length; index++) {
            result = (result << 8) + unsign(buffer[index]);
        }
        return result;
    }

    /**
     * Reconstructs a number that is represented by more than one byte in a network packet in little-endian order, for
     * the very few protocol values that are sent in this quirky way.
     *
     * @param buffer the byte array containing the packet data
     * @param start the index of the first byte containing a numeric value
     * @param length the number of bytes making up the value
     * @return the reconstructed number
     */
    @SuppressWarnings("SameParameterValue")
    public static long bytesToNumberLittleEndian(byte[] buffer, int start, int length) {
        long result = 0;
        for (int index = start + length - 1; index >= start; index--) {
            result = (result << 8) + unsign(buffer[index]);
        }
        return result;
    }

    /**
     * Writes a number to the specified byte array field, breaking it into its component bytes in big-endian order.
     * If the number is too large to fit in the specified number of bytes, only the low-order bytes are written.
     *
     * @param number the number to be written to the array
     * @param buffer the buffer to which the number should be written
     * @param start where the high-order byte should be written
     * @param length how many bytes of the number should be written
     */
    public static void numberToBytes(int number, byte[] buffer, int start, int length) {
        for (int index = start + length - 1; index >= start; index--) {
            buffer[index] = (byte)(number & 0xff);
            number = number >> 8;
        }
    }

    /**
     * Converts the bytes that make up an internet address into the corresponding integer value to make
     * it easier to perform bit-masking operations on them.
     *
     * @param address an address whose integer equivalent is desired
     *
     * @return the integer corresponding to that address
     */
    public static long addressToLong(InetAddress address) {
        long result = 0;
        for (byte element : address.getAddress()) {
            result = (result << 8) + unsign(element);
        }
        return result;
    }

    /**
     * Checks whether two internet addresses are on the same subnet.
     *
     * @param prefixLength the number of bits within an address that identify the network
     * @param address1 the first address to be compared
     * @param address2 the second address to be compared
     *
     * @return true if both addresses share the same network bits
     */
    public static boolean sameNetwork(int prefixLength, InetAddress address1, InetAddress address2) {
        if (logger.isDebugEnabled()) {
            logger.debug("Comparing address " + address1.getHostAddress() + " with " + address2.getHostAddress() + ", prefixLength=" + prefixLength);
        }
        long prefixMask = 0xffffffffL & (-1 << (32 - prefixLength));
        return (addressToLong(address1) & prefixMask) == (addressToLong(address2) & prefixMask);
    }

    /**
     * Convert a pitch value reported by a device to the corresponding percentage (-100% to +100%, where normal,
     * unadjusted pitch has the value 0%).
     *
     * @param pitch the reported device pitch
     * @return the pitch as a percentage
     */
    public static double pitchToPercentage(long pitch) {
        return (pitch - 1048567) / 10485.76;
    }

    /**
     * Convert a pitch value reported by a device to the corresponding multiplier (0.0 to 2.0, where normal, unadjusted
     * pitch has the multiplier 1.0).
     *
     * @param pitch the reported device pitch
     * @return the implied pitch multiplier
     */
    public static double pitchToMultiplier(long pitch) {
        return pitch / 1048576.0;
    }

    /**
     * Writes the entire remaining contents of the buffer to the channel. May complete in one operation, but the
     * documentation is vague, so this keeps going until we are sure.
     *
     * @param buffer the data to be written
     * @param channel the channel to which we want to write data
     *
     * @throws IOException if there is a problem writing to the channel
     */
    public static void writeFully(ByteBuffer buffer, WritableByteChannel channel) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * Figure out the track time that corresponds to a half-frame number (75 frames per second, so 150 half-frames).
     *
     * @param halfFrame the half-frame that we are interested in knowing the time for
     *
     * @return the number of milliseconds into a track that the specified half-frame begins
     */
    public static long halfFrameToTime(long halfFrame) {
        return halfFrame * 100 / 15;
    }

    /**
     * Convert a track position (time) into the corresponding half-frame value (75 frames per second, so 150 half-frames).
     *
     * @param milliseconds how long a track has been playing for
     *
     * @return the half-frame that contains that part of the track
     */
    public static int timeToHalfFrame(long milliseconds) {
        return (int) (milliseconds * 15 / 100);
    }

    /**
     * Convert a track position (time) into the corresponding rounded half-frame value (75 frames per second, so 150 half-frames).
     *
     * @param milliseconds how long a track has been playing for
     *
     * @return the nearest half-frame that contains that part of the track
     */
    public static int timeToHalfFrameRounded(long milliseconds) {
        return Math.round(milliseconds * 0.15f);
    }

    /**
     * Used to allow locking operations against named resources, such as files being fetched by
     * {@link org.deepsymmetry.beatlink.data.CrateDigger}, to protect against race conditions where
     * one thread creates the file and another thinks it has already been downloaded and tries to
     * parse the partial file.
     */
    private static final Map<String, Object> namedLocks = new HashMap<String, Object>();

    /**
     * Counts the threads that are currently using a named lock, so we can know when it can be
     * removed from the maps.
     */
    private static final Map<String, Integer> namedLockUseCounts = new HashMap<String, Integer>();

    /**
     * Obtain an object that can be synchronized against to provide exclusive access to a named resource,
     * given its unique name. Used with file canonical path names by {@link org.deepsymmetry.beatlink.data.CrateDigger}
     * to protect against race conditions where one thread creates the file and another thinks it has already been
     * downloaded and tries to parse the partial file.
     *
     * Once the exclusive lock is no longer needed, {@link #freeNamedLock(String)} should be called with the same
     * name so the lock can be garbage collected if no other threads are now using it.
     *
     * @param name uniquely identifies some resource to which exclusive access is needed
     * @return an object that can be used with a {@code synchronized} block to guarantee exclusive access to the resource
     */
    public synchronized static Object allocateNamedLock(String name) {
        Object result = namedLocks.get(name);
        if (result != null) {
            namedLockUseCounts.put(name, namedLockUseCounts.get(name) + 1);
            return result;
        }
        namedLockUseCounts.put(name, 1);
        result = new Object();
        namedLocks.put(name, result);
        return result;
    }

    /**
     * Indicate that an object obtained from {@link #allocateNamedLock(String)} is no longer needed by the caller, so
     * it is eligible for garbage collection if no other threads have it allocated.
     *
     * @param name uniquely identifies some resource to which exclusive access was previously needed
     */
    public synchronized static void freeNamedLock(String name) {
        int count = namedLockUseCounts.get(name);
        if (count > 1) {
            namedLockUseCounts.put(name, count - 1);
        } else {
            namedLocks.remove(name);
            namedLockUseCounts.remove(name);
        }
    }

    /**
     * Prevent instantiation.
     */
    private Util() {
        // Nothing to do.
    }
}
