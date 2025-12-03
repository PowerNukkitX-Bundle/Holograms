package org.powernukkitx.holograms.entity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.CustomEntityDefinition;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.IChunk;
import cn.nukkit.level.particle.FloatingTextParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.ServerScriptDebugDrawerPacket;
import cn.nukkit.network.protocol.types.debugshape.TextDebugShape;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.powernukkitx.placeholderapi.PlaceholderAPI;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


@Getter
@Setter
public class EntityHologram extends Entity implements CustomEntity {

    public static final String IDENTIFIER = "powernukkitx:hologram";

    public static final String SPACING = "spacing";
    public static final String LINES = "lines";

    private static final Field text_id;

    private final HashMap<Integer, List<String>> lineCache = new HashMap<>();

    static {
        try {
            text_id = FloatingTextParticle.class.getDeclaredField("entityId");
            text_id.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private float spacing = 0.3f;
    private List<String> lines;

    public EntityHologram(IChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        if(!this.namedTag.containsFloat(SPACING)) {
            this.namedTag.putFloat(SPACING, 0.3f);
        }
        this.spacing = this.namedTag.getFloat(SPACING);
        if(!this.namedTag.containsList(LINES)) {
            this.namedTag.putList(LINES, new ListTag<>(Tag.TAG_String));
        }
        this.lines = new ArrayList<>(this.namedTag.getList(LINES, StringTag.class).getAll().stream().map(StringTag::parseValue).toList());
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putFloat(SPACING, spacing);
        this.namedTag.putList(LINES, new ListTag<>(lines.stream().map(StringTag::new).toList()));
    }

    @Override
    public boolean teleport(Vector3 pos) {
        return super.teleport(pos);
    }

    @Override
    public @NotNull String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    @SneakyThrows
    public void spawnTo(Player player) {
        if(this.closed) return;
        if (!this.hasSpawned.containsKey(player.getLoaderId()) && this.chunk != null && player.getUsedChunks().contains(Level.chunkHash(this.chunk.getX(), this.chunk.getZ()))) {
            this.hasSpawned.put(player.getLoaderId(), player);
            Location loc = this.getLocation();
            ServerScriptDebugDrawerPacket packet = new ServerScriptDebugDrawerPacket();
            List<String> lines = this.getDisplayLines(player);
            for (int i = lines.size() - 1; i >= 0; i--) {
                loc.y += spacing;
                TextDebugShape shape = new TextDebugShape(loc.asVector3f(), Color.WHITE, lines.get(i));
                shape.networkId = ((this.getId()) << 32) | (i & 0xffffffffL);
                packet.shapes.add(shape);
            }
            lineCache.put(player.getLoaderId(), new ArrayList<>(lines));
            player.dataPacket(packet);
        }
    }

    @Override
    public void despawnFrom(Player player) {
        if (this.hasSpawned.containsKey(player.getLoaderId())) {
            ServerScriptDebugDrawerPacket packet = new ServerScriptDebugDrawerPacket();
            for (int i = lines.size() - 1; i >= 0; i--) {
                TextDebugShape shape = new TextDebugShape(Vector3.ZERO.asVector3f(), Color.WHITE, "");
                shape.networkId = ((this.getId()) << 32) | (i & 0xffffffffL);
                packet.shapes.add(shape);
            }
            player.dataPacket(packet);
            this.hasSpawned.remove(player.getLoaderId());
            this.lineCache.remove(player.getLoaderId());
        }
    }

    protected List<String> getDisplayLines(Player player) {
        if(Server.getInstance().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return lines.stream().map(s -> PlaceholderAPI.get().processPlaceholders(player, s)).toList();
        } else return lines;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if(isClosed()) return false;
        for(Player player : getViewers().values()) {
            List<String> sentLines = this.lineCache.get(player.getLoaderId());
            if(sentLines.size() != lines.size()) {
                hasSpawned.remove(player.getLoaderId());
                spawnTo(player);
                continue;
            }
            List<String> curLines = getDisplayLines(player);
            Location loc = this.getLocation();
            ServerScriptDebugDrawerPacket packet = new ServerScriptDebugDrawerPacket();
            for (int i = sentLines.size() - 1; i >= 0; i--) {
                loc.y += spacing;
                String curLine = curLines.get(i);
                if(!sentLines.get(i).equals(curLine)) {
                    sentLines.set(i, curLine);
                    TextDebugShape shape = new TextDebugShape(loc.asVector3f(), Color.WHITE, curLine);
                    shape.networkId = ((this.getId()) << 32) | (i & 0xffffffffL);
                    packet.shapes.add(shape);
                }
            }
            if(!packet.shapes.isEmpty()) player.dataPacket(packet);
        }
        return super.onUpdate(currentTick);
    }

    @Override
    public void kill() {
    }

    public static CustomEntityDefinition definition() {
        return CustomEntityDefinition.simpleBuilder(IDENTIFIER)
                .eid(IDENTIFIER)
                .hasSpawnEgg(false)
                .isSummonable(false)
                .build();
    }
}
