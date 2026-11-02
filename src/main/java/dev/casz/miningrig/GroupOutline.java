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
    /**
     * Precomputed per-cell owner and one-block exterior mask. The older
     * groupContains() path calculated 13-16 squared distances on EVERY
     * wall/fluid scan (and repeatedly checked all eight neighbors). This
     * immutable cache is built once on outline creation or world load,
     * without world/chunk access. 256x256 groups use roughly 70 KiB.
     */
    private final byte[] ownerCache;
    private final BitSet expandedCells;
    private final int cacheWidth,cacheHeight;
    private final int topY;
    private final List<BlockPos> markers;

    private GroupOutline(int minX,int minZ,int width,int height,BitSet cells,
                         int topY,List<BlockPos> markers){
        this.minX=minX;this.minZ=minZ;this.width=width;this.height=height;
        this.cells=(BitSet)cells.clone();
        this.count=cells.cardinality();this.topY=topY;
        this.markers=List.copyOf(markers);
        this.cacheWidth=width+2;this.cacheHeight=height+2;
        this.ownerCache=new byte[cacheWidth*cacheHeight];
        this.expandedCells=new BitSet(cacheWidth*cacheHeight);
        // Includes a one-block ring outside the marked shape; inner holes
        // still have their correct external boundary.
        for(int dz=-1;dz<=height;dz++)for(int dx=-1;dx<=width;dx++){
            int x=minX+dx,z=minZ+dz,index=(dz+1)*cacheWidth+dx+1;
            boolean core=has(x,z);
            boolean expanded=core;
            if(!expanded)for(int stepX=-1;stepX<=1&&!expanded;stepX++)
                for(int stepZ=-1;stepZ<=1&&!expanded;stepZ++)
                    if(has(x+stepX,z+stepZ))expanded=true;
            if(!expanded)continue;
            expandedCells.set(index);
            int best=-1;long nearest=Long.MAX_VALUE;
            for(int i=0;i<markers.size();i++){
                BlockPos p=markers.get(i);
                int ax=Math.abs(p.getX()-x),az=Math.abs(p.getZ()-z);
                int reach=core?ComputerEntity.MAX_RADIUS:ComputerEntity.MAX_RADIUS+1;
                if(ax>reach||az>reach)continue;
                long dist=(long)ax*ax+(long)az*az;
                if(dist<nearest){nearest=dist;best=i;}
            }
            ownerCache[index]=(byte)(best+1); // 0=uncovered, 1..16=owner
        }
    }
    public List<BlockPos> markers(){return markers;}
    public int topY(){return topY;}
    public int requiredDrills(){return markers.size();}
    public BlockPos marker(int n){return markers.get(n);}
    /** O(1) world-coordinate lookup for interior and exterior group cells. */
    private int cacheIndex(int x,int z){
        int dx=x-minX+1,dz=z-minZ+1;
        return dx>=0&&dz>=0&&dx<cacheWidth&&dz<cacheHeight
            ?dz*cacheWidth+dx:-1;
    }
    public int owner(int x,int z){
        int index=cacheIndex(x,z);
        return index<0?-1:Byte.toUnsignedInt(ownerCache[index])-1;
    }
    /**
     * Repeated maximum-new-coverage planner. Every potential tip must be
     * inside the marked hole. Each iteration picks the one 41x41-square
     * footprint that covers the most STILL-uncovered excavation cells.
     *
     * Unlike the former row-scan heuristic (which pushed heads toward the
     * same corner), this prioritizes large, distinct work zones and avoids
     * almost-touching markers. A summed-area table keeps each selection
     * O(width * height) even for the full 256x256 mask.
     */
    public GroupOutline plan(int tipY){
        int radius=ComputerEntity.MAX_RADIUS;
        int stride=width+1;
        int[] prefix=new int[(width+1)*(height+1)];
        BitSet remaining=(BitSet)cells.clone();
        int left=count;
        List<BlockPos> planned=new ArrayList<>();
        while(left>0){
            for(int z=0;z<height;z++){
                int row=0;
                for(int x=0;x<width;x++){
                    if(remaining.get(z*width+x))row++;
                    prefix[(z+1)*stride+x+1]=prefix[z*stride+x+1]+row;
                }
            }
            int bestGain=0,bestX=-1,bestZ=-1;
            long bestSpread=Long.MIN_VALUE;
            for(int z=0;z<height;z++)for(int x=0;x<width;x++){
                if(!cells.get(z*width+x))continue;
                int x0=Math.max(0,x-radius),x1=Math.min(width-1,x+radius);
                int z0=Math.max(0,z-radius),z1=Math.min(height-1,z+radius);
                int gain=prefix[(z1+1)*stride+x1+1]-prefix[z0*stride+x1+1]
                    -prefix[(z1+1)*stride+x0]+prefix[z0*stride+x0];
                if(gain<bestGain||gain==0)continue;
                long spread=Long.MAX_VALUE;
                for(BlockPos p:planned){
                    long dx=(long)(minX+x)-p.getX(),dz=(long)(minZ+z)-p.getZ();
                    spread=Math.min(spread,dx*dx+dz*dz);
                }
                if(gain==bestGain && spread<=bestSpread)continue;
                bestGain=gain;bestSpread=spread;bestX=x;bestZ=z;
            }
            if(bestGain==0)return null;
            planned.add(new BlockPos(minX+bestX,tipY,minZ+bestZ));
            if(planned.size()>ComputerEntity.MAX_DRILLS)return null;
            for(int z=Math.max(0,bestZ-radius);z<=Math.min(height-1,bestZ+radius);z++)
                for(int x=Math.max(0,bestX-radius);x<=Math.min(width-1,bestX+radius);x++)
                    if(remaining.get(z*width+x)){
                        remaining.clear(z*width+x);
                        left--;
                    }
        }
        GroupOutline result=new GroupOutline(minX,minZ,width,height,cells,tipY,planned);
        return result.coveredBy(planned)?result:null;
    }
    public int count(){return count;}
    public boolean has(int x,int z){
        return x>=minX&&z>=minZ&&x<minX+width&&z<minZ+height
            &&cells.get((z-minZ)*width+x-minX);
    }
    public boolean contains(int x,int z,boolean expanded){
        if(!expanded)return has(x,z);
        int index=cacheIndex(x,z);
        return index>=0&&expandedCells.get(index);
    }
    public String encode(){
        StringBuilder result=new StringBuilder(minX+","+minZ+","+width+","+height
            +","+topY+";"+Base64.getEncoder().encodeToString(cells.toByteArray()));
        for(BlockPos p:markers)result.append(";").append(p.getX())
            .append(",").append(p.getY()).append(",").append(p.getZ());
        return result.toString();
    }
    public static GroupOutline decode(String encoded){
        if(encoded==null||encoded.isBlank()||encoded.length()>15000)return null;
        try{
            String[] parts=encoded.split(";");
            String[] sizes=parts[0].split(",");
            int x=Integer.parseInt(sizes[0]),z=Integer.parseInt(sizes[1]);
            int w=Integer.parseInt(sizes[2]),h=Integer.parseInt(sizes[3]);
            if(w<1||h<1||w>MAX_SIDE||h>MAX_SIDE||parts.length<2
                ||sizes.length<5||parts.length-2>ComputerEntity.MAX_DRILLS)return null;
            BitSet bits=BitSet.valueOf(Base64.getDecoder().decode(parts[1]));
            if(bits.length()>w*h||bits.isEmpty())return null;
            int top=Integer.parseInt(sizes[4]);
            List<BlockPos> marks=new ArrayList<>();
            for(int i=2;i<parts.length;i++){
                String[] c=parts[i].split(",");
                BlockPos p=new BlockPos(Integer.parseInt(c[0]),Integer.parseInt(c[1]),
                    Integer.parseInt(c[2]));
                marks.add(p);
            }
            if(marks.isEmpty())return null;
            GroupOutline result=new GroupOutline(x,z,w,h,bits,top,marks);
            return result.coveredBy(marks)?result:null;
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
        if(marks.size()<3||marks.size()>TargetChalkItem.MAX_POINTS
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
                case REPLACE,GROUP->painted;
                case ADD->before||painted;
                case CUT->before&&!painted;
            };
            if(active)bits.set((z-loZ)*width+x-loX);
        }
        if(bits.isEmpty())return null;
        GroupOutline result=new GroupOutline(loX,loZ,width,height,bits,0,List.of());
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
