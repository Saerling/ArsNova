# Iconos de puntos (estética thinking orbs): un orbe de puntos sobre rejilla esférica; los puntos grandes dibujan el símbolo.
import math, sys, json
from PIL import Image, ImageDraw
S=8  # supermuestreo de la máscara (108*S)
def mask_shiori():
    im=Image.new('F',(108*S,108*S),0); px=im.load()
    for j in range(108*S):
        for i in range(108*S):
            x=i/S-54; y=j/S-54; r=math.hypot(x,y); v=0
            if r>=17.6: v=max(v, math.exp(-(r-18.6)/3.2) if r>18.6 else 1.0)   # halo pegado al horizonte
            # disco de acreción: elipse muy plana que cruza por delante
            dy=abs(y-1.5); ax=abs(x)
            if ax<34: v=max(v, max(0,1-dy/2.6)*(1-(ax/34)**3))
            px[i,j]=min(1,v)
    return im
def bez(p0,p1,p2,p3,n=24): return [tuple((1-t)**3*a+3*(1-t)**2*t*b+3*(1-t)*t*t*c+t**3*d for a,b,c,d in zip(p0,p1,p2,p3)) for t in [k/n for k in range(n+1)]]
def mask_ars():
    im=Image.new('L',(108*S,108*S),0); d=ImageDraw.Draw(im)
    # plumín ampliado alrededor del centro para llenar el orbe
    def T(p,k=1.22,c=(54,56)): return ((c[0]+(p[0]-c[0])*k)*S,(c[1]+(p[1]-c[1])*k)*S-2*S)
    pts=bez((54,30),(61,42),(68,52),(68,62))+[(62,76),(46,76)]+bez((40,62),(40,52),(47,42),(54,30))
    d.polygon([T(p) for p in pts],fill=255)
    a=T((52.6,36)); b=T((55.4,57)); d.rectangle([a,b],fill=0)
    c=T((54,58)); rr=4.6*S; d.ellipse([c[0]-rr,c[1]-rr,c[0]+rr,c[1]+rr],fill=0)
    a=T((44,79.5)); b=T((64,83.5)); d.rounded_rectangle([a,b],radius=2*S,fill=255)
    return im.convert('F').point(lambda v:v/255)
def mask_fyx():
    im=Image.open('/home/claude/mio/fyx/docs/icono.png').convert('L')
    w=im.width; k=0.62  # el símbolo ocupa el centro: se lleva al círculo seguro
    im=im.resize((int(108*S/k),)*2,Image.LANCZOS); o=(im.width-108*S)//2
    return im.crop((o,o,o+108*S,o+108*S)).convert('F').point(lambda v:v/255)
def dots(mask,N=17,R=31.0,bulge=0.55,fondo=True):
    px=mask.load(); out=[]
    for a in range(N):
        for b in range(N):
            u=(a-(N-1)/2)/((N-1)/2); v=(b-(N-1)/2)/((N-1)/2); d=math.hypot(u,v)
            if d>1.0001: continue
            # rejilla esférica: se aprieta hacia el borde como en una esfera vista de frente
            f=(math.sin(d*math.pi/2)*bulge+d*(1-bulge))/d if d>0 else 1
            x=54+u*f*R; y=54+v*f*R
            prof=math.sqrt(max(0,1-d*d))           # 1 en el centro, 0 en el limbo
            # media de la máscara en la celda
            cell=R/((N-1)/2)*0.5; s=0;n=0
            for dx in (-.6,-.2,.2,.6):
                for dy in (-.6,-.2,.2,.6):
                    i=int((x+dx*cell)*S); j=int((y+dy*cell)*S)
                    if 0<=i<108*S and 0<=j<108*S: s+=px[i,j]; n+=1
            m=s/max(n,1)
            pitch=R/((N-1)/2)*(0.55+0.45*prof)
            if m>0.18: out.append((round(x,2),round(y,2),round(pitch*(0.20+0.30*min(1,m*1.15)),2),1))
            elif fondo: out.append((round(x,2),round(y,2),round(pitch*0.13,2),0))
    return out
def render(ds,size,bg=(0,0,0)):
    k=size*4/108; im=Image.new('RGB',(size*4,size*4),bg); d=ImageDraw.Draw(im)
    for x,y,r,b in ds:
        c=255 if b else 96
        d.ellipse([(x-r)*k,(y-r)*k,(x+r)*k,(y+r)*k],fill=(c,c,c))
    # recorte de icono (zona visible 72 de 108) en squircle aproximado
    o=int(18*k); im=im.crop((o,o,size*4-o,size*4-o)).resize((size,size),Image.LANCZOS)
    return im
if __name__=='__main__':
    N=int(sys.argv[1]) if len(sys.argv)>1 else 17
    res={n:dots(m(),N) for n,m in (('shiori',mask_shiori),('arsnova',mask_ars),('fyx',mask_fyx))}
    json.dump(res,open('dots.json','w'))
    W=Image.new('RGB',(3*330,330+120),(40,40,44))
    for i,(n,ds) in enumerate(res.items()):
        W.paste(render(ds,320),(i*330+5,5)); W.paste(render(ds,96),(i*330+5,340)); W.paste(render(ds,48),(i*330+120,340))
        print(n,len(ds),sum(1 for d in ds if d[3]))
    W.save('/tmp/iconos.png')
