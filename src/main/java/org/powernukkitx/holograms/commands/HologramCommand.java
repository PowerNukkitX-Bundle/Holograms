package org.powernukkitx.holograms.commands;

import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.PluginCommand;
import cn.nukkit.form.element.custom.ElementInput;
import cn.nukkit.form.element.custom.ElementSlider;
import cn.nukkit.form.window.CustomForm;
import cn.nukkit.form.window.SimpleForm;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.ServerScriptDebugDrawerPacket;
import cn.nukkit.network.protocol.types.debugshape.TextDebugShape;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.powernukkitx.holograms.Holograms;
import org.powernukkitx.holograms.entity.EntityHologram;

import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class HologramCommand extends PluginCommand<Holograms> {

    public HologramCommand() {
        super("holograms", Holograms.get());
        this.setDescription("Manages your placeholders");
        this.setPermission("hologram.command");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if(sender.isPlayer()) {
            new SimpleForm("Holograms")
                    .addButton("Create Hologram", player -> {
                        new CustomForm()
                                .addElement(new ElementInput("Hologram Name (Not visible) - This is for you so you can edit holograms."))
                                .addElement(new ElementSlider("How many lines do you want to add", 1, 16, 1, 1))
                                .onSubmit((player1, response) -> {
                                    int amount = (int) response.getSliderResponse(1);
                                    CustomForm lines = new CustomForm();
                                    for(int i = 0; i < amount; i++) lines.addElement(new ElementInput(""));
                                    lines.onSubmit((player2, response1) -> {
                                        String identifier = response.getInputResponse(0);
                                        if(identifier.isEmpty())  identifier = response1.getInputResponse(0);
                                        List<String> lineList = response1.getResponses().int2ObjectEntrySet().stream().sorted(Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey)).map(o -> (String) o.getValue()).toList();
                                        Holograms.get().createHologram(identifier, player2, lineList);
                                    });
                                    lines.send(player1);
                                })
                                .title("Create Hologram")
                                .send(player);
                    })
                    .addButton("Edit Hologram", player -> {
                        SimpleForm holograms = new SimpleForm("Holograms");
                        Arrays.stream(player.getLevel().getEntities()).filter(entity -> entity instanceof EntityHologram).forEach(entity -> {
                            EntityHologram hologram = (EntityHologram) entity;
                            holograms.addButton(entity.getNameTag() + "\nX: " + entity.getFloorX() + ", Y: " + entity.getFloorY() + ", Z: " + entity.getFloorZ(), player1 -> {
                                new SimpleForm(entity.getNameTag())
                                        .addButton("Change Name (Not visible)", player2 -> {
                                            new CustomForm("Change Name")
                                                    .addElement(new ElementInput("Hologram Name (Not visible) - This is for you so you can edit holograms.", "", entity.getNameTag()))
                                                    .onSubmit((player3, response) -> {
                                                        String name = response.getInputResponse(0);
                                                        entity.setNameTag(name);
                                                        player3.sendMessage("§aChanged hologram name to " + name + ".");
                                                    })
                                                    .send(player2);
                                        })
                                        .addButton("Change Text", player2 -> {
                                            SimpleForm lines = new SimpleForm(entity.getNameTag());
                                            List<String> content = hologram.getLines();
                                            for(int i = 0; i < content.size(); i++) {
                                                String line = content.get(i);
                                                int finalI = i;
                                                lines.addButton(line, player3 -> {
                                                   new SimpleForm(line)
                                                           .addButton("Edit line", player4 -> {
                                                                new CustomForm("Edit line")
                                                                        .addElement(new ElementInput("", "", line))
                                                                        .onSubmit((player5, response) -> {
                                                                            content.set(finalI, response.getInputResponse(0));
                                                                            hologram.respawnToAll();
                                                                        }).send(player4);
                                                           })
                                                           .addButton("Move", player4 -> {
                                                               SimpleForm move = new SimpleForm("Move line");
                                                               if(finalI != 0){
                                                                   String lineUp = content.get(finalI-1);
                                                                   move.addButton("Move up\n" + lineUp, player5 -> {
                                                                        content.set(finalI, lineUp);
                                                                        content.set(finalI-1, line);
                                                                        hologram.respawnToAll();
                                                                   });
                                                               }
                                                               if(finalI != content.size()-1) {
                                                                   String lineDown = content.get(finalI+1);
                                                                   move.addButton("Move down\n" + lineDown, player5 -> {
                                                                       content.set(finalI, lineDown);
                                                                       content.set(finalI+1, line);
                                                                       hologram.respawnToAll();
                                                                   });
                                                               }
                                                               move.send(player4);
                                                           })
                                                           .addButton("§cDelete line", player4 -> {
                                                               content.remove(finalI);
                                                               ServerScriptDebugDrawerPacket packet = new ServerScriptDebugDrawerPacket();
                                                               TextDebugShape shape = new TextDebugShape(Vector3.ZERO.asVector3f(), Color.WHITE, "");
                                                               shape.networkId = ((hologram.getId()) << 32) | (finalI & 0xffffffffL);
                                                               packet.shapes.add(shape);
                                                               Server.broadcastPacket(hologram.getViewers().values(), packet);
                                                               hologram.respawnToAll();
                                                           })
                                                           .send(player3);
                                                });
                                            }
                                            lines.addButton("New line", player3 -> {
                                                new CustomForm("New line")
                                                        .addElement(new ElementInput("Text"))
                                                        .onSubmit((player4, response) -> {
                                                            content.add(response.getInputResponse(0));
                                                            hologram.respawnToAll();
                                                        })
                                                        .send(player3);
                                            });
                                            lines.send(player2);
                                        })
                                        .addButton("Teleport", player2 -> {
                                            hologram.teleport(player);
                                            hologram.respawnToAll();
                                        })
                                        .addButton("§l§4Delete", player2 -> {
                                            entity.close();
                                            player2.sendMessage("§cDeleted " + hologram.getNameTag() + ".");
                                        })
                                        .send(player1);
                            });
                        });
                        holograms.send(player);
                    })
                    .send(sender.asPlayer());
            return true;
        }
        return false;
    }
}
