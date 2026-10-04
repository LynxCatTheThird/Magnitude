package dev.magnitude.content;

import net.minecraft.network.chat.Component;

/** Stable stored IDs shared by command names, tool cycling and visible descriptions. */
public enum ToolMode {
    MULTIPLY(0,"multiply"), ADD(1,"add"), SET(2,"set"), SWAP(3,"swap"), TRANSFER(4,"transfer");
    public final int storedId;
    public final String command;
    ToolMode(int storedId,String command){this.storedId=storedId;this.command=command;}
    public Component label(){return Component.translatable("tool.magnitude.mode."+command);}
    public ToolMode next(){return fromStored((storedId+1)%5);}
    public static ToolMode fromStored(int id){
        return switch(Math.clamp(id,0,4)){
            case 1 -> ADD;case 2 -> SET;case 3 -> SWAP;case 4 -> TRANSFER;default -> MULTIPLY;
        };
    }
}
