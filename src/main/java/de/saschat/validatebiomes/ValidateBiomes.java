package de.saschat.validatebiomes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.BossBarCommands;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.commands.TitleCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileWriter;
import java.lang.ref.Reference;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ValidateBiomes implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatch, context, selection) -> {
            dispatch.register(literal("validatebiomes").requires(a -> a.hasPermission(4)).executes((ctx) -> {
                begin(ctx, 6400);
                return 0;
            }).then(argument("something", IntegerArgumentType.integer()).executes((ctx) -> {
                begin(ctx, IntegerArgumentType.getInteger(ctx, "something"));
                return 0;
            })));
        });//*/
    }

    public record OutputLine(
        String biomeName, String type, boolean present, Integer x, Integer y
    ) {

    }

    public static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public interface ResolutionType {
        BlockPos resolve(int i);

        String name();

        String type();
    }

    private void begin(CommandContext<CommandSourceStack> ctx, int value) {
        ctx.getSource().sendSuccess(() -> Component.literal("Validation has begun. A report will be produced."), false);
        new Thread(() -> {
            var access = ctx.getSource().getLevel().registryAccess();
            var biome = access.registry(Registries.BIOME).get();
            var biomeSet = biome.entrySet();
            var structure = access.registry(Registries.STRUCTURE).get();
            var structureSet = structure.entrySet();

            ctx.getSource().sendSuccess(() -> Component.literal(String.format("%d biomes found in registry.", biomeSet.size())), false);
            ctx.getSource().sendSuccess(() -> Component.literal(String.format("%d structures found in registry.", structureSet.size())), false);

            Vec3 x = ctx.getSource().getPosition();
            BlockPos c = new BlockPos((int) x.x, (int) x.y, (int) x.z);
            List<OutputLine> list = new LinkedList<>();
            List<ResolutionType> resolve = new LinkedList<>();


            for (Map.Entry<ResourceKey<Biome>, Biome> biome1 : biomeSet)
                resolve.add(new ResolutionType() {
                    @Override
                    public BlockPos resolve(int i) {
                        var ret = ctx.getSource().getLevel().findClosestBiome3d(a -> a.is(biome1.getKey().location()), c, value, 64, 32);
                        if (ret == null)
                            return null;
                        return ret.getFirst();
                    }

                    @Override
                    public String name() {
                        return biome1.getKey().toString();
                    }

                    @Override
                    public String type() {
                        return "biome";
                    }
                });
            for (Map.Entry<ResourceKey<Structure>, Structure> structure1 : structureSet)
                resolve.add(new ResolutionType() {
                    @Override
                    public BlockPos resolve(int i) {
                        var tk = TagKey.create(Registries.STRUCTURE, structure1.getKey().location());

                        var ret = ctx.getSource().getLevel().getChunkSource().getGenerator().findNearestMapStructure(ctx.getSource().getLevel(), HolderSet.direct(structure.wrapAsHolder(structure1.getValue())), c, 100, false);
                        if (ret == null)
                            return null;
                        return ret.getFirst();
                    }

                    @Override
                    public String name() {
                        return structure1.getKey().toString();
                    }

                    @Override
                    public String type() {
                        return "structure";
                    }
                });

            int i = 0;
            for (ResolutionType rkbe : resolve) {
                var ret = rkbe.resolve(value);
                String status = String.format("%.02f", ((float) i / resolve.size()) * 100) + '%';
                if (ret == null) {
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s not found (%s)", rkbe.name(), status)), false);
                    list.add(new OutputLine(rkbe.name(), rkbe.type(), false, 0, 0));
                } else {
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s found at %d %d %d (%s)", rkbe.name(), ret.getX(), ret.getY(), ret.getZ(), status)), false);
                    list.add(new OutputLine(rkbe.name(), rkbe.type(), false, 0, 0));
                }
                i++;
            }

            String name = String.format("report%016X.json", Instant.now().getEpochSecond());
            File output = new File(FabricLoader.getInstance().getGameDir().toFile(), name);
            try (FileWriter writer = new FileWriter(output)) {
                gson.toJson(list, writer);
                writer.flush();
                ctx.getSource().sendSuccess(() -> Component.literal(String.format("Saved to %s", output.getAbsolutePath())), false);
            } catch (Exception ex) {
                ex.printStackTrace();
                ctx.getSource().sendSuccess(() -> Component.literal("Error occurred while saving. Check server console."), false);
            }
        }).start();
    }//*/
}
