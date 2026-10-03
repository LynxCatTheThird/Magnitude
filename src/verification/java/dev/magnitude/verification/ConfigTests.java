package dev.magnitude.verification;

import dev.magnitude.Magnitude;
import dev.magnitude.config.*;
import dev.magnitude.core.*;
import dev.magnitude.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class ConfigTests {
    private static void check(boolean condition,String label,Consumer<String> passed){if(!condition)throw new AssertionError(label);passed.accept(label);}
    public static void run(MinecraftServer server,Consumer<String> passed)throws Exception {
        var old=Magnitude.settings;var path=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("magnitude.json");
        byte[] contents=Files.exists(path)?Files.readAllBytes(path):null;
        var backup=path.resolveSibling("magnitude.json.bak");byte[] backupContents=Files.exists(backup)?Files.readAllBytes(backup):null;
        var player=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"ConfigVerifier"),ClientInformation.createDefault());
        var normal=player.createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS).withSuppressedOutput();
        var admin=server.createCommandSourceStack();var dispatcher=server.getCommands().getDispatcher();
        Path temp=Files.createTempDirectory("magnitude-config-test-");
        try {
            Magnitude.settings=new Settings();
            var service=new ConfigService(s->s.save(temp.resolve("settings.json")));
            var original=Magnitude.settings;
            check(service.server(normal,0,Map.of("terrainDamage",1d)).code().equals("permission")&&Magnitude.settings==original,"config service rejects unauthorized server mutation",passed);
            check(service.server(admin,0,Map.of("terrainDamage",1d,"maximum",Double.NaN)).code().equals("invalid")&&!Magnitude.settings.terrainDamage,"invalid batch cannot partially change live settings",passed);
            check(service.server(admin,0,Map.of("blocksPerTick",2.5)).code().equals("invalid"),"integer config field rejects fractional value",passed);
            check(service.server(admin,0,Map.of("standingPressure",2d)).code().equals("invalid"),"boolean config field rejects arbitrary numeric value",passed);
            check(service.server(admin,0,Map.of("unregistered",1d)).code().equals("invalid"),"unknown config field is rejected",passed);
            check(service.server(admin,0,Map.of("terrainDamage",1d,"maximum",49.75)).success()&&Magnitude.settings.terrainDamage&&Magnitude.settings.maximum==49.75,"finite fractional size setting applies as one persisted batch",passed);
            var loaded=new com.google.gson.Gson().fromJson(Files.readString(temp.resolve("settings.json")),Settings.class);
            check(loaded.maximum==49.75&&loaded.terrainDamage,"persisted configuration matches acknowledged state",passed);
            check(service.server(admin,0,Map.of("terrainDamage",0d)).code().equals("conflict")&&Magnitude.settings.terrainDamage,"stale config revision cannot overwrite a newer edit",passed);
            check(service.server(admin,1,Map.of("blocksPerTick",0d)).success()&&Files.exists(temp.resolve("settings.json.bak")),"successful replacement keeps recoverable previous configuration",passed);
            var previous=Magnitude.settings;
            Files.writeString(temp.resolve("blocked"),"regular file blocks parent directory creation");
            var failing=new ConfigService(s->s.save(temp.resolve("blocked/settings.json")));
            check(failing.server(admin,0,Map.of("terrainDamage",0d)).code().equals("saveFailed")&&Magnitude.settings==previous&&failing.revision()==0,"actual file write failure does not publish candidate or advance revision",passed);
            var state=EntityState.of(player);long personal=state.configRevision;
            check(service.personal(player,personal,Map.of("terrain",1d,"resize",1d)).success()&&state.terrainEnabled&&state.acceptResize,"personal batch changes only requesting player's permissions",passed);
            check(service.personal(player,personal,Map.of("carry",1d)).code().equals("conflict")&&!state.acceptCarry,"stale personal edit is rejected",passed);
            check(service.personal(player,state.configRevision,Map.of("carry",1d,"pressure",Double.POSITIVE_INFINITY)).code().equals("invalid")&&!state.acceptCarry,"invalid personal batch cannot partially grant consent",passed);
            check(ConfigNetworking.apply(player,new ConfigRequest(1,2,ConfigService.INSTANCE.revision(),Map.of("terrainDamage",0d))).code().equals("permission"),"forged settings packet cannot grant server administrator permission",passed);
            check(ConfigNetworking.apply(player,new ConfigRequest(2,1,-1,Map.of("carry",1d))).code().equals("invalid"),"network patch cannot bypass optimistic revision checks",passed);
            check(ConfigNetworking.apply(player,new ConfigRequest(3,9,0,Map.of())).code().equals("invalid"),"unknown network scope rejected",passed);
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),server.registryAccess());
            try {
                var packet=new ConfigRequest(8,1,state.configRevision,Map.of("carry",1d));ConfigRequest.CODEC.encode(buffer,packet);
                check(packet.equals(ConfigRequest.CODEC.decode(buffer)),"bounded config patch protocol roundtrip",passed);
                buffer.clear();buffer.writeVarLong(1);buffer.writeVarInt(2);buffer.writeVarLong(0);buffer.writeVarInt(17);
                boolean rejected=false;try{ConfigRequest.CODEC.decode(buffer);}catch(IllegalArgumentException error){rejected=true;}
                check(rejected,"oversized network batch rejected before reading entries",passed);
                buffer.clear();buffer.writeVarLong(1);buffer.writeVarInt(1);buffer.writeVarLong(0);buffer.writeVarInt(2);
                buffer.writeUtf("carry");buffer.writeDouble(1);buffer.writeUtf("carry");buffer.writeDouble(0);
                rejected=false;try{ConfigRequest.CODEC.decode(buffer);}catch(IllegalArgumentException error){rejected=true;}
                check(rejected,"duplicate network fields are rejected",passed);
                buffer.clear();var snapshot=new ConfigSnapshot(8,false,new com.google.gson.Gson().toJson(ConfigNetworking.view(player,new ConfigService.Result("ready",""))));
                ConfigSnapshot.CODEC.encode(buffer,snapshot);check(snapshot.equals(ConfigSnapshot.CODEC.decode(buffer)),"settings snapshot protocol roundtrip",passed);
            }finally{buffer.release();}
            Magnitude.settings.terrainDamage=false;
            var view=ConfigNetworking.view(player,new ConfigService.Result("ready",""));
            check(!view.terrainEffective()&&view.terrainReason().equals("serverOff"),"effective settings explain server gate despite personal opt-in",passed);
            Magnitude.settings.terrainDamage=true;state.terrainEnabled=false;
            check(ConfigNetworking.view(player,new ConfigService.Result("ready","")).terrainReason().equals("personalOff"),"effective settings explain personal gate",passed);
            check(dispatcher.execute("magnitude config player carry on",normal)==1&&state.acceptCarry,"canonical personal command applies shared service",passed);
            long revision=state.configRevision;
            check(dispatcher.execute("magnitude config player carry off",normal)==1&&!state.acceptCarry&&state.configRevision==revision+1,"canonical consent edit advances personal revision",passed);
            check(dispatcher.execute("magnitude config server blocksPerTick 12",admin)==1&&Magnitude.settings.blocksPerTick==12,"canonical server command persists through shared service",passed);
            long global=ConfigService.INSTANCE.revision();
            check(dispatcher.execute("magnitude config server walkDamageFactor 3",admin)==1&&Magnitude.settings.walkDamageFactor==3&&ConfigService.INSTANCE.revision()==global+1,"canonical physics parameter advances server revision",passed);
            check(dispatcher.execute("magnitude config server terrainDamage true",admin)==1&&Magnitude.settings.terrainDamage,"canonical server switch delegates to shared service",passed);
            check(!dispatcher.getRoot().getChild("magnitude").getChild("config").getChild("server").canUse(normal),"canonical command tree hides server edits from ordinary players",passed);
            Dimensions.set(player,2,0);
            check(dispatcher.execute("magnitude scale reset",normal)==1&&Dimensions.target(player)==1,"scale reset is a direct sibling of scale get",passed);
            check(dispatcher.parse("magnitude action release",normal).getExceptions().isEmpty(),"grouped action command remains available",passed);
            var expectedRoots=java.util.Set.of("scale","config","action","carry","tool","random","diagnostics","menu");
            check(dispatcher.getRoot().getChild("magnitude").getChildren().stream().map(com.mojang.brigadier.tree.CommandNode::getName).collect(java.util.stream.Collectors.toSet()).equals(expectedRoots),"only canonical domain roots are registered",passed);
            for(String oldCommand:new String[]{"get","set 2","multiply 2","add 1","height 4","reset","consent carry true","terrain true","pickup","release","throw","blow","stomp","ability","ride","physics status","physics enable","admin set @s 2","scale get reset","tool mode 0","tool value 2","tool duration 20","carry hand","random 1 2 20"}) {
                var parsed=dispatcher.parse("magnitude "+oldCommand,admin);
                check(parsed.getReader().canRead()||!parsed.getExceptions().isEmpty(),"removed CLI does not parse completely: "+oldCommand,passed);
            }
            check(dispatcher.execute("magnitude config food minecraft:apple 2",admin)==1&&Magnitude.settings.foodFactors.get("minecraft:apple")==2,"food settings use canonical configuration scope",passed);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(dev.magnitude.content.Content.TUNER));
            check(dispatcher.execute("magnitude tool set mode swap",normal)==1&&dev.magnitude.content.ToolItem.data(player.getMainHandItem()).getIntOr("operation",-1)==3,"named tool mode maps to persisted operation",passed);
            check(dispatcher.execute("magnitude tool set value 2",normal)==1&&dev.magnitude.content.ToolItem.data(player.getMainHandItem()).getDoubleOr("value",0)==2,"canonical tool value setting applies",passed);
            check(dispatcher.parse("magnitude carry position hand",normal).getReader().canRead()==false,"carry position is explicit",passed);
            check(dispatcher.parse("magnitude scale targets set @e[type=minecraft:pig] 2 0",admin).getReader().canRead()==false,"administrator target resizing belongs to scale scope",passed);
            check(ConfigService.INSTANCE.reload(normal).code().equals("permission"),"reload uses shared administrator authorization",passed);
            var saved=Magnitude.settings;Files.writeString(path,"{ broken json");
            check(ConfigService.INSTANCE.reload(admin).code().equals("saveFailed")&&Magnitude.settings==saved,"malformed reload preserves confirmed live state",passed);
        }finally {
            Magnitude.settings=old;
            if(contents==null)Files.deleteIfExists(path);else Files.write(path,contents);
            if(backupContents==null)Files.deleteIfExists(backup);else Files.write(backup,backupContents);
            try(var stream=Files.walk(temp)){for(var entry:stream.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(entry);}
        }
    }
}
