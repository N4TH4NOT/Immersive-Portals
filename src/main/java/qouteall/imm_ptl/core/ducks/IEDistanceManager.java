package qouteall.imm_ptl.core.ducks;

import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

public interface IEDistanceManager {
    
    java.util.List<Ticket> portal_getTicketSet(long chunkPos);
    
    
}
