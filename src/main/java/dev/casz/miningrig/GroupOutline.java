package dev.casz.miningrig;

import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * One world-aligned horizontal hole for a Mining Computer's drilling group.
 * Coordinates are X/Z, independent of each drill's moving Y position.
 * A bounded bitmask makes arbitrary chalk polygons, additions and cut-outs
 * persistent without requiring any loaded terrain or per-layer world scans.
 */
public final class GroupOutline {
    public static final int MAX_SIDE=256;
    private final int minX,minZ,width,height;
    private final BitSet cells;
    private final int count;

    private GroupOutline(int minX,int minZ,int width,int height,BitSet cells){
        this.minX=minX;this.minZ=minZ;this.width=width;this.height=height;
        this.cells=(BitSet)cells.clone();
        this.count=cells.cardinality();
    }
    public int count(){return count;}
    public boolean has(int x,int z){
        return x>=minX&&z>=minZ&&x<minX+width&&z<minZ+height
            &&cells.get((z-minZ)*width+x-minX);
    }
    public boolean contains(int x,int z,boolean expanded){
        if(has(x,z))return true;
        if(!expanded)return false;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            if(has(x+dx,z+dz))return true;
        return false;
    }
    public String encode(){
        return minX+","+minZ+","+width+","+height+";"
            +Base64.getEncoder().encodeToString(cells.toByteArray());
    }
    public static GroupOutline decode(String encoded){
        if(encoded==null||encoded.isBlank()||encoded.length()>15000)return null;
        try{
            String[] parts=encoded.split(";",2);
            String[] sizes=parts[0].split(",");
            int x=Integer.parseInt(sizes[0]),z=Integer.parseInt(sizes[1]);
            int w=Integer.parseInt(sizes[2]),h=Integer.parseInt(sizes[3]);
            if(w<1||h<1||w>MAX_SIDE||h>MAX_SIDE||parts.length!=2)return null;
            BitSet bits=BitSet.valueOf(Base64.getDecoder().decode(parts[1]));
            if(bits.length()>w*h||bits.isEmpty())return null;
            return new GroupOutline(x,z,w,h,bits);
        }catch(RuntimeException ignored){return null;}
    }
    private static boolean inside(List<int[]> poly,int x,int z){
        boolean inside=false;
        for(int i=0,j=poly.size()-1;i<poly.size();j=i++){
            int[] a=poly.get(j),b=poly.get(i);
            long cross=(long)(x-a[0])*(b[1]-a[1])-(long)(z-a[1])*(b[0]-a[0]);
            if(cross==0&&x>=Math.min(a[0],b[0])&&x<=Math.max(a[0],b[0])
                    &&z>=Math.min(a[1],b[1])&&z<=Math.max(a[1],b[1]))return true;
            if((a[1]>z)!=(b[1]>z)
                    &&x<a[0]+(double)(z-a[1])*(b[0]-a[0])/(b[1]-a[1]))
                inside=!inside;
        }
        return inside;
    }
    public static GroupOutline apply(List<BlockPos> marks,GroupOutline previous,
                                      ChalkOutline.Operation operation){
        if(marks.size()<3||marks.size()>128
                ||(operation!=ChalkOutline.Operation.REPLACE&&previous==null))return null;
        List<int[]> vertices=new ArrayList<>();
        int loX=Integer.MAX_VALUE,loZ=Integer.MAX_VALUE,hiX=Integer.MIN_VALUE,hiZ=Integer.MIN_VALUE;
        for(BlockPos at:marks){
            int x=at.getX(),z=at.getZ();
            if(vertices.isEmpty()||vertices.get(vertices.size()-1)[0]!=x
                    ||vertices.get(vertices.size()-1)[1]!=z)vertices.add(new int[]{x,z});
            loX=Math.min(loX,x);loZ=Math.min(loZ,z);
            hiX=Math.max(hiX,x);hiZ=Math.max(hiZ,z);
        }
        if(vertices.size()<3)return null;
        if(previous!=null&&operation!=ChalkOutline.Operation.REPLACE){
            loX=Math.min(loX,previous.minX);loZ=Math.min(loZ,previous.minZ);
            hiX=Math.max(hiX,previous.minX+previous.width-1);
            hiZ=Math.max(hiZ,previous.minZ+previous.height-1);
        }
        long w=(long)hiX-loX+1,h=(long)hiZ-loZ+1;
        if(w<1||h<1||w>MAX_SIDE||h>MAX_SIDE)return null;
        int width=(int)w,height=(int)h;
        BitSet bits=new BitSet(width*height);
        for(int z=loZ;z<=hiZ;z++)for(int x=loX;x<=hiX;x++){
            boolean painted=inside(vertices,x,z);
            boolean before=previous!=null&&previous.has(x,z);
            boolean active=switch(operation){
                case REPLACE->painted;
                case ADD->before||painted;
                case CUT->before&&!painted;
            };
            if(active)bits.set((z-loZ)*width+x-loX);
        }
        if(bits.isEmpty())return null;
        GroupOutline result=new GroupOutline(loX,loZ,width,height,bits);
        if(previous!=null&&result.count()==previous.count()){
            boolean same=true;
            for(int z=loZ;z<=hiZ&&same;z++)for(int x=loX;x<=hiX;x++)
                if(result.has(x,z)!=previous.has(x,z)){same=false;break;}
            if(same)return null;
        }
        return result;
    }
    /** Every included cell needs a designated drill within 20 blocks. */
    public boolean coveredBy(List<BlockPos> drills){
        if(drills.isEmpty())return false;
        for(BlockPos drill:drills)if(!has(drill.getX(),drill.getZ()))return false;
        for(int z=minZ;z<minZ+height;z++)for(int x=minX;x<minX+width;x++){
            if(!has(x,z))continue;
            boolean covered=false;
            for(BlockPos drill:drills)
                if(Math.abs(drill.getX()-x)<=ComputerEntity.MAX_RADIUS
                        &&Math.abs(drill.getZ()-z)<=ComputerEntity.MAX_RADIUS){
                    covered=true;break;
                }
            if(!covered)return false;
        }
        return true;
    }
}
