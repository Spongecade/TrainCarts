package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfig;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentEmpty;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Manages one or more trees of attachments on a more general level.
 * It controls aspects that are difficult for an attachment to do by itself,
 * in particular:
 * <ul>
 * <li>Creating new attachments from their configurations</li>
 * <li>Tracking what players can see the attachments, calling makeVisible
 * and makeHidden when required</li>
 * <li>Updating the position (root) transformation for the root attachments</li>
 * <li>Seating players inside seat-capable attachments</li>
 * <li>Providing context when needed (for example, that of a Traincarts Minecart)</li>
 * </ul>
 */
public interface AttachmentManager {

    /**
     * Gets the world the attachments are in
     * 
     * @return world
     */
    org.bukkit.World getWorld();

    /**
     * Gets the players who can currently view the attachments
     *
     * @return Collection of players
     */
    Collection<Player> getViewers();

    /**
     * Gets the Attachment Viewers who can currently view the attachments
     *
     * @return Collection of attachment viewers
     */
    Collection<AttachmentViewer> getAttachmentViewers();

    /**
     * Gets or creates the AttachmentViewer associated with a Player
     *
     * @param player Player
     * @return attachment viewer for this player
     */
    AttachmentViewer asAttachmentViewer(Player player);

    /**
     * Gets the {@link AttachmentTypeRegistry} used to find and create new attachments from
     * configuration.
     * 
     * @return attachment type registry
     */
    default AttachmentTypeRegistry getTypeRegistry() {
        return AttachmentTypeRegistry.instance();
    }

    /**
     * Creates a new attachment by loading it from configuration.
     * No further operations, such as attaching it, are performed yet.
     * 
     * @param attachmentConfig Attachment Configuration
     * @return created attachment
     */
    default Attachment createAttachment(AttachmentConfig attachmentConfig) {
        AttachmentType attachmentType = getTypeRegistry().findOrEmpty(attachmentConfig.typeId());

        ConfigurationNode config = attachmentConfig.config();
        Attachment attachment = attachmentType.createController(config);
        AttachmentInternalState state = attachment.getInternalState();
        state.manager = this;
        state.onLoad(this.getClass(), attachmentType, config);

        for (AttachmentConfig childAttachmentConfig : attachmentConfig.children()) {
            attachment.addChild(createAttachment(childAttachmentConfig));
        }

        return attachment;
    }
}
