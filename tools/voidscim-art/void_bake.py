import glob, os, shutil
from PIL import Image

# Smooth void colorway: indigo shadow -> bright violet highlight (matches the
# ::appearance void hair depth, richer than the flat luster fill).
STOPS = [(0,(10,4,30)),(30,(16,6,46)),(70,(42,16,92)),(105,(72,28,142)),
         (140,(108,46,192)),(160,(132,60,216)),(255,(150,72,228))]
def ramp(g):
    for i in range(len(STOPS)-1):
        g0,c0=STOPS[i]; g1,c1=STOPS[i+1]
        if g0<=g<=g1:
            t=(g-g0)/(g1-g0) if g1>g0 else 0
            return tuple(round(c0[k]+(c1[k]-c0[k])*t) for k in range(3))
    return STOPS[-1][1]
# explicit non-gray maps
GOLD={(255,169,25):(196,140,255),(255,198,66):(214,168,255)}   # gold trim -> lavender accent
RED ={(228,0,0):(205,140,255),(218,0,0):(185,115,248),(165,0,0):(150,75,215),(150,0,0):(128,55,195)}  # plume -> lavender

def bake(src, dst):
    os.makedirs(dst, exist_ok=True)
    for f in sorted(glob.glob(src+'/frame_*.png')):
        base=os.path.basename(f); img=Image.open(f).convert('RGBA')
        out=Image.new('RGBA',img.size,(0,0,0,0)); sp,dp=img.load(),out.load()
        for y in range(img.size[1]):
            for x in range(img.size[0]):
                r,g,b,a=sp[x,y]
                if a==0: continue
                if r==g==b: dp[x,y]=(*ramp(r),a)
                elif (r,g,b) in GOLD: dp[x,y]=(*GOLD[(r,g,b)],a)
                elif (r,g,b) in RED:  dp[x,y]=(*RED[(r,g,b)],a)
                else: dp[x,y]=(r,g,b,a)
        out.save(dst+'/'+base)
        sc=f+'.json'
        if os.path.exists(sc): shutil.copy(sc, dst+'/'+base+'.json')
    print('baked', src, '->', dst, len(glob.glob(dst+'/frame_*.png')),'frames')

bake('out/body_base','out/body_void')
bake('out/legs_base','out/legs_void')
bake('out/helm_base','out/helm_void')   # re-bake helm metal too (was gray+fill, now baked)
