package com.bergerkiller.bukkit.tc.controller.spawnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.properties.SavedTrainProperties;
import com.bergerkiller.bukkit.tc.properties.defaults.DefaultProperties;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Stores information about a train prior to spawning
 */
public class SpawnableGroup implements TrainCarts.Provider {
    private final TrainCarts plugin;
    private final List<SpawnableMember> members = new ArrayList<>();
    private final ConfigurationNode config;
    private CenterMode centerMode = CenterMode.NONE;

    /**
     * How many blocks to check further forwards when spawning to see whether
     * the spawned train can move forwards after spawning.
     */
    private static final double CAN_MOVE_DISTANCE = 2.0;

    /**
     * @deprecated Pass TrainCarts plugin instance using {@link #SpawnableGroup(TrainCarts)} instead
     */
    @Deprecated
    public SpawnableGroup() {
        this(TrainCarts.plugin);
    }

    public SpawnableGroup(TrainCarts plugin) {
        this.plugin = plugin;
        this.config = new ConfigurationNode();
    }

    @Override
    public TrainCarts getTrainCarts() {
        return plugin;
    }

    /**
     * Gets the TrainCarts plugin instance that manages this SpawnableGroup
     *
     * @return TrainCarts plugin instance
     * @deprecated Use {@link #getTrainCarts()} instead
     */
    @Deprecated
    public TrainCarts getPlugin() {
        return this.plugin;
    }

    /**
     * Gets the train group configuration to be applied to the train's properties
     * after spawning.
     * 
     * @return train configuration
     */
    public ConfigurationNode getConfig() {
        return this.config;
    }

    /**
     * Gets the name format of the saved group information.
     * If no name is set in the configuration, the default
     * train name format is returned instead.
     *
     * @return saved train name format
     */
    public TrainNameFormat getNameFormat() {
        return StandardProperties.TRAIN_NAME_FORMAT.readFromConfig(this.config)
                .orElse(StandardProperties.TRAIN_NAME_FORMAT.getDefault());
    }

    /**
     * Gets the name under which this spawnable group was previously
     * saved in the saved train properties store. If no such name
     * is stored, generates a random name based on the name format,
     * instead.
     *
     * @return saved train name, or a default generated name.
     */
    public String getSavedName() {
        if (config.contains("savedName")) {
            return config.get("savedName", "dummyname");
        } else {
            return getNameFormat().generate(1);
        }
    }

    /**
     * Gets the way the train should be centered when spawning
     * 
     * @return center mode
     */
    public CenterMode getCenterMode() {
        return this.centerMode;
    }

    /**
     * Sets the way the train should be centered when spawning
     * 
     * @param mode Center mode to set to
     */
    public void setCenterMode(CenterMode mode) {
        this.centerMode = mode;
    }

    /**
     * Gets all the Minecarts part of this group
     * 
     * @return list of spawnable members
     */
    public List<SpawnableMember> getMembers() {
        return this.members;
    }

    /**
     * Adds a new Minecart to the end of this group
     * 
     * @param config
     * @return SpawnableMember added
     */
    public SpawnableMember addMember(ConfigurationNode config) {
        SpawnableMember newMember = new SpawnableMember(this, config);
        this.members.add(newMember);
        return newMember;
    }

    /**
     * Adds a new Minecart to the end of this group, using the configuration
     * of an existing spawnable member.
     * 
     * @param member
     * @return SpawnableMember added
     */
    public SpawnableMember addMember(SpawnableMember member) {
        SpawnableMember newMember = member.cloneWithGroup(this);
        this.members.add(newMember);
        return newMember;
    }

    /**
     * Gets the full configuration of the spawnable group, including
     * the configuration of all spawned carts.
     *
     * @return full configuration
     */
    public ConfigurationNode getFullConfig() {
        ConfigurationNode fullConfig = this.config.clone();
        List<ConfigurationNode> cartConfigList = fullConfig.getNodeList("carts");
        for (int i = this.members.size() - 1; i >= 0; i--) {
            cartConfigList.add(this.members.get(i).getConfig().clone());
        }
        return fullConfig;
    }

    /**
     * Gets an unmodifiable List of saved train properties that impose a spawn limit
     * when this train is spawned.
     *
     * @return List of saved train properties that contain spawn limits that this spawned group
     *         takes part in.
     */
    public List<SavedTrainProperties> getActiveSavedTrainSpawnLimits() {
        Optional<List<String>> names = StandardProperties.ACTIVE_SAVED_TRAIN_SPAWN_LIMITS.readFromConfig(this.config);
        if (names.isPresent()) {
            List<SavedTrainProperties> propsList = new ArrayList<>(names.get().size());
            for (String name : names.get()) {
                SavedTrainProperties props = getTrainCarts().getSavedTrains().getProperties(name);
                if (props != null && props.getSpawnLimit() >= 0) {
                    propsList.add(props);
                }
            }
            return Collections.unmodifiableList(propsList);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Checks any active saved train spawn limits to see if, currently, the number of already spawned
     * trains is at the configured limit. Returns true if this spawnable group should not be spawned
     * because it would exceed a set spawn limit.<br>
     * <br>
     * <b>Does not check the per-world minecart limit</b>
     *
     * @return True if spawning this group would exceed saved train spawn limits
     */
    public boolean isExceedingSpawnLimit() {
        Optional<List<String>> names = StandardProperties.ACTIVE_SAVED_TRAIN_SPAWN_LIMITS.readFromConfig(this.config);
        if (names.isPresent()) {
            for (String name : names.get()) {
                SavedTrainProperties props = getTrainCarts().getSavedTrains().getProperties(name);
                if (props == null) {
                    continue;
                }
                int limit = props.getSpawnLimit();
                if (limit >= 0 && props.getSpawnLimitCurrentCount() >= limit) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds the carts of a saved train as members to this spawnable group and merges the train
     * configuration.
     *
     * @param savedTrainProperties SavedTrainProperties to add to this group
     * @return List of newly added SpawnableMember
     */
    public List<SpawnableMember> addTrainWithConfig(SavedTrainProperties savedTrainProperties) {
        if (savedTrainProperties == null || savedTrainProperties.isEmpty()) {
            return Collections.emptyList();
        }

        List<SpawnableMember> addedMembers = addTrainWithConfig(savedTrainProperties.getConfig());

        // If these saved train properties apply a spawn limit, add the saved train properties name
        // to the active spawn limits property of this train.
        if (!addedMembers.isEmpty() && savedTrainProperties.getSpawnLimit() >= 0) {
            StandardProperties.ACTIVE_SAVED_TRAIN_SPAWN_LIMITS.addSavedTrainToConfig(
                    this.config, savedTrainProperties.getName());
        }

        return addedMembers;
    }

    /**
     * Applies the train YAML configuration to this spawnable group and adds the "carts"
     * represented inside.
     *
     * @param savedConfig Saved train YAML config
     * @return List of newly added SpawnableMember
     */
    public List<SpawnableMember> addTrainWithConfig(ConfigurationNode savedConfig) {
        for (String key : savedConfig.getKeys()) {
            if (key.equals("carts")) continue;
            this.config.set(key, savedConfig.get(key));
        }
        List<ConfigurationNode> cartConfigList = savedConfig.getNodeList("carts");
        List<SpawnableMember> newMembers = new ArrayList<>(cartConfigList.size());
        for (int i = cartConfigList.size() - 1; i >= 0; i--) {
            newMembers.add(this.addMember(cartConfigList.get(i)));
        }
        return newMembers;
    }

    /**
     * Gets the total length of all the members of this spawnable group
     * 
     * @return total length
     */
    public double getTotalLength() {
        if (this.members.isEmpty()) {
            return 0.0;
        } else {
            return this.members.stream().mapToDouble(SpawnableMember::getLength).sum() +
                    (double) (this.members.size() - 1) * TCConfig.cartDistanceGap;
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("{").append("center=").append(this.centerMode);
        str.append(", types=[");
        boolean first = true;
        for (SpawnableMember member : this.members) {
            if (first) {
                first = false;
            } else {
                str.append(", ");
            }
            str.append(member.toString());
        }
        str.append("]}");
        return str.toString();
    }

    /**
     * Finds the locations in the world to spawn all the members of this SpawnableGroup.
     * Starts looking from the spawn position on the initial rail block.
     * Returns null if spawning isn't possible, because there isn't enough space,
     * or because the start rail block isn't valid rails.
     * 
     * @param startRailBlock Rail block to spawn the train on
     * @param forwardDirection Direction in which to spawn the train
     * @param mode {@link SpawnMode} that defines how the train is spawned
     * @return spawn location list, or null if spawning the train is not possible
     */
    public SpawnLocationList findSpawnLocations(Block startRailBlock, Vector forwardDirection, SpawnMode mode) {
        return findSpawnLocations(RailPiece.create(startRailBlock), forwardDirection, mode);
    }

    /**
     * Finds the locations in the world to spawn all the members of this SpawnableGroup.
     * Starts looking from the spawn position on the initial rail piece.
     * Returns null if spawning isn't possible, because there isn't enough space,
     * or because the start rail piece isn't valid rails.
     * 
     * @param startRails Rail piece information to spawn the train on
     * @param forwardDirection Direction in which to spawn the train
     * @param mode {@link SpawnMode} that defines how the train is spawned
     * @return spawn location list, or null if spawning the train is not possible
     */
    public SpawnLocationList findSpawnLocations(RailPiece startRails, Vector forwardDirection, SpawnMode mode) {
        if (startRails == null || startRails.isNone()) {
            return null;
        }

        RailState state = RailState.getSpawnState(startRails);
        if (state.motionVector().dot(forwardDirection) < 0.0) {
            state.position().invertMotion();
        }

        return findSpawnLocations(state, mode);
    }

    /**
     * Finds the locations in the world to spawn all the members of this SpawnableGroup.
     * Uses the start location to find the rails covering that position, and then
     * tries to work out all places to spawn the members.
     * 
     * @param startLocation The start location on rails to start spawning on
     * @param forwardDirection Direction in which to spawn the train
     * @param mode {@link SpawnMode} that defines how the train is spawned
     * @return spawn location list, or null if spawning the train is not possible
     */
    public SpawnLocationList findSpawnLocations(Location startLocation, Vector forwardDirection, SpawnMode mode) {
        RailPiece piece = RailType.findRailPiece(startLocation);
        if (piece == null || piece.isNone()) {
            return null;
        }

        RailState state = new RailState();
        state.setRailPiece(piece);
        state.position().setLocation(startLocation);
        RailType.loadRailInformation(state); // Note: do before, to avoid weird entering from the side
        state.setMotionVector(forwardDirection);
        state.loadRailLogic().getPath().snap(state.position(), state.railBlock());

        return findSpawnLocations(state, mode);
    }

    /**
     * Finds the locations in the world to spawn all the members of this SpawnableGroup.
     * Uses the initial RailState to walk down (and backwards) the rails to calculate
     * the positions.
     * 
     * @param startState Initial state on the rails to start walking down the rails
     * @param mode {@link SpawnMode} that defines how the train is spawned
     * @return spawn location list, or null if spawning the train is not possible
     */
    public SpawnLocationList findSpawnLocations(RailState startState, SpawnMode mode) {
        if (this.members.isEmpty()) {
            return null;
        }
        if (startState.railType() == RailType.NONE) {
            return null;
        }

        // Whether the edge of the cart should be at the start position
        boolean edgeAtStart = (mode == SpawnMode.DEFAULT_EDGE) || (mode == SpawnMode.REVERSE_EDGE);

        // Just use the initial position of the rail state, no 'walking' needed
        if (!edgeAtStart && this.members.size() == 1) {
            SpawnLocationList result = new SpawnLocationList();
            result.addMember(this.members.get(0), startState.motionVector(), startState.positionLocation());

            // Check whether additional rails are available beyond placement of the member itself
            TrackWalkingPoint walker = new TrackWalkingPoint(startState);
            walker.skipFirst();
            result.can_move = walker.move(0.5*this.members.get(0).getLength() + CAN_MOVE_DISTANCE);
            return result;
        }

        // CENTER logic requires walking in both directions
        if (mode == SpawnMode.CENTER) {
            double halfLength = 0.5 * this.getTotalLength();

            // Too small, just place all members on the exact same position
            // In practise this will never happen, as it requires a gap of 0 to be configured
            if (halfLength < 1e-10) {
                SpawnLocationList result = new SpawnLocationList();
                Vector forward = startState.motionVector();
                Location location = startState.positionLocation();
                for (SpawnableMember member : this.members) {
                    result.addMember(member, forward, location);
                }

                // Check whether additional rails are available
                TrackWalkingPoint walker = new TrackWalkingPoint(startState);
                walker.skipFirst();
                result.can_move = walker.move(CAN_MOVE_DISTANCE);
                return result;
            }

            // Separate the members into two halves relative to the center
            List<SpawnableMember> backward = new ArrayList<SpawnableMember>(this.members.size());
            List<SpawnableMember> forward = new ArrayList<SpawnableMember>(this.members.size());
            double backwardOffset = 0.0;
            double forwardOffset = 0.0;
            {
                double accumLength = 0.0;
                double prevMemberLength = 0.0;
                boolean isForwardPortion = false;
                for (SpawnableMember member : this.members) {
                    if (isForwardPortion) {
                        forward.add(member);
                        continue;
                    }

                    double memberStartLength = 0.5 * member.getLength() + prevMemberLength;

                    // Check if this member is considered part of the forward half
                    // If so, switch modes and calculate the offsets as appropriate
                    double distanceBeyondHalf = ((accumLength + memberStartLength) - halfLength);
                    if (distanceBeyondHalf >= 0.0) {
                        backwardOffset = (halfLength - accumLength);
                        forwardOffset = distanceBeyondHalf;
                        isForwardPortion = true;
                        forward.add(member);
                        continue;
                    }

                    // Member is definitely part of the backward half, so add it
                    accumLength += memberStartLength;
                    prevMemberLength = 0.5 * member.getLength() + TCConfig.cartDistanceGap;
                    backward.add(member);
                }
                if (!isForwardPortion) {
                    // All carts are backward, which means we have to calculate offsets here
                    backwardOffset = (halfLength - accumLength);
                    forwardOffset = halfLength; // no forward carts, just check forward offset
                }
            }

            // Reverse list of backward members so we iterate from center outwards
            Collections.reverse(backward);

            // Compute the members when walking backwards first, then reverse the list to make it back to front
            SpawnLocationList result = new SpawnLocationList();
            {
                TrackWalkingPoint walker = new TrackWalkingPoint(startState.cloneAndInvertMotion());
                walker.skipFirst();

                // Check there is space behind us
                // If the number of backward members is 0, then this checks whether totalLength()/2
                // of space is available behind us, which would be part of a very long forward member.
                // The half-distance to the first cart is included in the backwardOffset
                // Hence we do (i > 0) at the start of the for loop below
                if (!walker.move(backwardOffset)) {
                    return null; // No space behind
                }

                for (int i = 0; i < backward.size(); i++) {
                    SpawnableMember member = backward.get(i);
                    if (i > 0 && !walker.move(0.5 * member.getLength())) {
                        return null; // Out of space behind
                    }
                    result.addMember(member,
                            walker.state.motionVector().multiply(-1.0),
                            Util.invertRotation(walker.state.positionLocation()));

                    // Last member has no gap
                    double gap = (i < (backward.size() - 1)) ? TCConfig.cartDistanceGap : 0.0;
                    if (!walker.move(0.5 * member.getLength() + gap)) {
                        return null; // Out of space behind
                    }
                }

                Collections.reverse(result.locations);
            }

            // Now add all remaining forward members to it
            {
                TrackWalkingPoint walker = new TrackWalkingPoint(startState);
                walker.skipFirst();

                // Similar to the backward check above, but for the forward member instead.
                if (!walker.move(forwardOffset)) {
                    return null; // No space forwards
                }

                for (int i = 0; i < forward.size(); i++) {
                    SpawnableMember member = forward.get(i);
                    if (i > 0 && !walker.move(0.5 * member.getLength())) {
                        return null; // Out of space forwards
                    }
                    result.addMember(member, walker.state.motionVector(), walker.state.positionLocation());

                    // Last member has no gap
                    double gap = (i < (forward.size() - 1)) ? TCConfig.cartDistanceGap : 0.0;
                    if (!walker.move(0.5 * member.getLength() + gap)) {
                        return null; // Out of space forwards
                    }
                }

                result.can_move = walker.move(CAN_MOVE_DISTANCE); // Check further rails are available
            }

            return result;
        }

        // DEFAULT spawning mode (DEFAULT_EDGE makes it not ignore the first half of the member length)
        if (mode == SpawnMode.DEFAULT || mode == SpawnMode.DEFAULT_EDGE) {
            SpawnLocationList result = new SpawnLocationList();
            TrackWalkingPoint walker = new TrackWalkingPoint(startState);
            walker.skipFirst();
            for (int i = 0; i < this.members.size(); i++) {
                SpawnableMember member = this.members.get(i);
                if (!edgeAtStart && i == 0) {
                    if (!walker.move(0.0)) {
                        return null; // Out of space
                    }
                } else {
                    if (!walker.move(0.5 * member.getLength())) {
                        return null; // Out of space
                    }
                }
                result.addMember(member, walker.state.motionVector(), walker.state.positionLocation());

                // Last member has no gap
                double gap = (i < (this.members.size() - 1)) ? TCConfig.cartDistanceGap : 0.0;
                if (!walker.move(0.5 * member.getLength() + gap)) {
                    return null; // Out of space
                }
            }
            result.can_move = walker.move(CAN_MOVE_DISTANCE); // Check further rails are available
            return result;
        }

        // REVERSE spawning mode (REVERSE_EDGE makes it not ignore the first half of the member length)
        if (mode == SpawnMode.REVERSE || mode == SpawnMode.REVERSE_EDGE) {
            SpawnLocationList result = new SpawnLocationList();
            TrackWalkingPoint walker = new TrackWalkingPoint(startState.cloneAndInvertMotion());
            walker.skipFirst();
            for (int i = this.members.size()-1; i >= 0; i--) {
                SpawnableMember member = this.members.get(i);
                if (!edgeAtStart && (i == (this.members.size()-1))) {
                    if (!walker.move(0.0)) {
                        return null; // Out of space
                    }
                } else {
                    if (!walker.move(0.5 * member.getLength())) {
                        return null; // Out of space
                    }
                }
                result.addMember(member,
                        walker.state.motionVector().multiply(-1.0),
                        Util.invertRotation(walker.state.positionLocation()));

                // Last member has no gap
                double gap = (i > 0) ? TCConfig.cartDistanceGap : 0.0;
                if (!walker.move(0.5 * member.getLength() + gap)) {
                    return null; // Out of space
                }
            }
            Collections.reverse(result.locations); // Make sure head is still at the same position
            result.can_move = walker.move(CAN_MOVE_DISTANCE); // Check further rails are available
            return result;
        }

        // Weird/unsupported mode
        return null;
    }

    /**
     * Takes the information of this spawnable group and spawns it as a new train.
     * The spawn locations and member information calculated using
     * {@link #findSpawnLocations(Location, Vector, SpawnMode)} is used.
     * Using the spawn location list of another group may yield unpredictable results.
     * 
     * @param spawnLocations The spawn locations for all the members of this group
     * @return newly spawned group
     */
    public MinecartGroup spawn(SpawnLocationList spawnLocations) {
        return MinecartGroupStore.spawn(this, spawnLocations);
    }

    /**
     * Takes the information of this spawnable group and spawns it as a new train.
     * The spawn locations and member information calculated using
     * {@link #findSpawnLocations(Location, Vector, SpawnMode)} is used.
     * Using the spawn location list of another group may yield unpredictable results.<br>
     * <br>
     * In addition to spawning makes the train move forwards (according to spawn locations)
     * at an initial speed.
     *
     * @param spawnLocations The spawn locations for all the members of this group
     * @param initialSpeed Initial forward (or backward, if negative) speed of the train
     * @return newly spawned group
     */
    public MinecartGroup spawn(SpawnLocationList spawnLocations, double initialSpeed) {
        return MinecartGroupStore.spawn(this, spawnLocations, initialSpeed);
    }

    /**
     * @deprecated Use {@link #fromConfig(TrainCarts, ConfigurationNode)} instead
     */
    @Deprecated
    public static SpawnableGroup fromConfig(ConfigurationNode savedConfig) {
        return fromConfig(TrainCarts.plugin, savedConfig);
    }

    /**
     * Creates a SpawnableGroup from the full contents of saved train properties
     *
     * @param savedTrainProperties Saved Train Properties from the saved train properties store
     * @return spawnable group
     */
    public static SpawnableGroup fromConfig(SavedTrainProperties savedTrainProperties) {
        SpawnableGroup result = new SpawnableGroup(savedTrainProperties.getTrainCarts());
        result.addTrainWithConfig(savedTrainProperties);
        return result;
    }

    /**
     * Creates a SpawnableGroup from the full contents of saved train properties, 
     * either from a real train or from the saved properties store.
     *
     * @param plugin TrainCarts plugin instance
     * @param savedConfig
     * @return spawnable group
     */
    public static SpawnableGroup fromConfig(TrainCarts plugin, ConfigurationNode savedConfig) {
        SpawnableGroup result = new SpawnableGroup(plugin);
        result.addTrainWithConfig(savedConfig);
        return result;
    }

    /**
     * Verifies whether the player can spawn this train, based on the individual carts being spawned.
     * If the player can not, the player is told why this is, and this method returns false.
     *
     * @param sender (or player)
     * @return True if spawning is permitted
     */
    public boolean checkSpawnPermissions(CommandSender sender) {
        // Verify cart types and use of pre-set inventory items
        boolean canHaveItems = false;
        for (SpawnableMember member : getMembers()) {
            if (!member.getPermission().handleMsg(sender,
                    Localization.SPAWN_DISALLOWED_TYPE.get(member.toString()))
            ) {
                return false;
            }

            if (canHaveItems) {
                continue;
            }
            if (member.hasInventoryItems()) {
                canHaveItems = Permission.SPAWNER_INVENTORY_ITEMS.has(sender);
                if (!canHaveItems) {
                    Localization.SPAWN_DISALLOWED_INVENTORY.message(sender);
                    return false;
                }
            }
        }

        // Verify set properties
        DefaultProperties defaults;
        if (sender instanceof Player) {
            defaults = TrainPropertiesStore.getDefaultsByPlayer((Player) sender);
        } else {
            defaults = TrainPropertiesStore.getDefaultsByName("default");
        }
        if (!defaults.checkSavedTrainPermissions(sender, this)) {
            return false;
        }

        return true;
    }

    /**
     * @deprecated Use {@link #parse(TrainCarts, String)} instead
     */
    @Deprecated
    public static SpawnableGroup parse(String typesText) {
        return parse(TrainCarts.plugin, typesText);
    }

    /**
     * Parses the contents of a types-encoded String. This is a String token
     * in the same format as is used on the 3rd/4th lines on spawner signs.
     *
     * @param plugin TrainCarts plugin instance
     * @param typesText
     * @return spawnable group parsed from the types text
     */
    public static SpawnableGroup parse(TrainCarts plugin, String typesText) {
        TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse(
                typesText, name -> plugin.getSavedTrains().findName(name));

        SpawnableGroup result = new SpawnableGroup(plugin);
        result.setCenterMode(pattern.centerMode());
        pattern.newGroupApplier().accept(result);
        return result;
    }

    /**
     * @deprecated Use {@link #ofMembers(TrainCarts, Iterable)} instead
     */
    @Deprecated
    public static SpawnableGroup ofMembers(Iterable<SpawnableMember> members) {
        return ofMembers(TrainCarts.plugin, members);
    }

    /**
     * Takes a list of members and turns it into a new group by calling {@link #addMember(SpawnableMember)}
     * for every member. The group configuration will be left to the defaults.
     *
     * @param plugin TrainCarts plugin instance
     * @param members The members to add
     * @return new spawnable group
     */
    public static SpawnableGroup ofMembers(TrainCarts plugin, Iterable<SpawnableMember> members) {
        SpawnableGroup group = new SpawnableGroup(plugin);
        for (SpawnableMember member : members) {
            group.addMember(member);
        }
        return group;
    }

    /**
     * Vanilla cart types that can be spawned with default properties by specifying
     * the single-character "code".
     */
    public static enum VanillaCartType {
        RIDEABLE('m', EntityType.MINECART),
        STORAGE('s', EntityType.MINECART_CHEST),
        POWERED('p', EntityType.MINECART_FURNACE),
        HOPPER('h', EntityType.MINECART_HOPPER),
        TNT('t', EntityType.MINECART_TNT);

        private final char code;
        private final EntityType type;

        private VanillaCartType(char code, EntityType type) {
            this.code = code;
            this.type = type;
        }

        public char getCode() {
            return this.code;
        }

        public EntityType getType() {
            return this.type;
        }

        @Override
        public String toString() {
            return Character.toString(this.code);
        }

        public static Optional<VanillaCartType> parse(char c) {
            c = Character.toLowerCase(c); //Note: might fail for turkish!
            for (VanillaCartType type : VanillaCartType.values()) {
                if (type.getCode() == c) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Ways of centering a train when spawning
     */
    public static enum CenterMode {
        NONE, MIDDLE, LEFT, RIGHT;

        public CenterMode next(CenterMode adjusted) {
            if (this == NONE || this == adjusted) {
                return adjusted;
            } else {
                return MIDDLE;
            }
        }
    }

    /**
     * Ways of positioning a train when spawning
     */
    public static enum SpawnMode {
        /**
         * <b>[^T][ ][ ][ ][H] -></b><br>
         * <br>
         * Default spawn mode places the back cart exactly at the start position,
         * and further carts in order into the direction as specified. The last
         * cart spawned furthest away is the front or head of the train.
         */
        DEFAULT,
        /**
         * <b>[T][ ][ ][ ][^H] -></b><br>
         * <br>
         * Similar to the {@link #DEFAULT} spawn mode, but instead of placing the
         * tail close to the start position and the head further away, the head
         * is placed close instead.
         */
        REVERSE,
        /**
         * <b>^[T][ ][ ][ ][H] -></b><br>
         * <br>
         * Similar to the {@link #DEFAULT} spawn mode, but places the first cart
         * at an offset so that the edge of the first cart is at the start position
         */
        DEFAULT_EDGE,
        /**
         * <b>[T][ ][ ][ ][H]^ -></b><br>
         * <br>
         * Similar to the {@link #REVERSE} spawn mode, but places the first cart
         * at an offset so that the edge of the first cart is at the start position
         */
        REVERSE_EDGE,
        /**
         * <b>[T][ ][^][ ][H] -></b><br>
         * <br>
         * Centers the train at the position, making sure the front cart is
         * positioned into the direction as specified.
         */
        CENTER
    }

    /**
     * Stores the results of finding the locations to spawn the members of a spawnable group.
     * Also stores what kind of centering mode was used in the end.
     */
    public static final class SpawnLocationList {
        /** The spawn locations for each member of the spawnable group */
        public final List<SpawnableMember.SpawnLocation> locations;
        /** Whether additional rails are available in front of the front cart */
        public boolean can_move;

        /**
         * Adds a spawnable member and the spawn information to this spawn location list
         * 
         * @param member
         * @param forward
         * @param location
         */
        public void addMember(SpawnableMember member, Vector forward, Location location) {
            this.locations.add(new SpawnableMember.SpawnLocation(member, forward, location));
        }

        public SpawnLocationList() {
            this.locations = new ArrayList<>();
            this.can_move = true;
        }

        /**
         * Loads all the chunks at the spawn locations to prepare it for spawning a train
         */
        public void loadChunks() {
            for (SpawnableMember.SpawnLocation loc : this.locations) {
                WorldUtil.loadChunks(loc.location, 2);
            }
        }

        /**
         * Checks whether any of the spawn locations are occupied by another train
         * 
         * @return True if occupied
         */
        public boolean isOccupied() {
            for (SpawnableMember.SpawnLocation loc : this.locations) {
                MinecartMember<?> member = MinecartMemberStore.getAt(loc.location);
                if (member != null && !member.isUnloaded() && !member.getEntity().isRemoved()) {
                    return true;
                }
            }
            return false;
        }
    }
}
