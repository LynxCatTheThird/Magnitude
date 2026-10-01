#!/usr/bin/env python3
"""Generate original pixel icons and data resources; uses only the Python standard library."""
from pathlib import Path
import json, struct, zlib, math
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
def put(path, obj):
 p=ROOT/path; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
def png(path, pixels, size=16):
 p=ROOT/path; p.parent.mkdir(parents=True,exist_ok=True)
 def chunk(kind, body): return struct.pack('>I',len(body))+kind+body+struct.pack('>I',zlib.crc32(kind+body)&0xffffffff)
 raw=b''.join(b'\x00'+bytes(sum(pixels[y*size:(y+1)*size],())) for y in range(size))
 p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
items={
 'amber_biscuit':('琥珀饼干','Amber Biscuit','biscuit',(232,181,74)),
 'azure_biscuit':('蔚蓝饼干','Azure Biscuit','biscuit',(74,184,220)),
 'balance_biscuit':('平衡饼干','Balance Biscuit','biscuit',(201,170,229)),
 'essence_reservoir':('精华储瓶','Essence Reservoir','bottle',(139,213,185)),
 'tuning_wand':('调谐杖','Tuning Wand','wand',(201,170,229)),
 'refraction_wand':('折射杖','Refraction Wand','wand',(74,184,220)),
 'rescue_harness':('救援背带','Rescue Harness','harness',(190,148,98)),
 'travel_blanket':('旅行毯','Travel Blanket','cloth',(215,161,171)),
 'silk_wing':('丝翼','Silk Wing','wing',(205,233,216)),
 'woven_hood':('编织兜帽','Woven Hood','hood',(118,166,136)),
 'woven_vest':('编织背心','Woven Vest','vest',(118,166,136)),
 'woven_trousers':('编织长裤','Woven Trousers','trousers',(118,166,136)),
 'cushioned_boots':('缓冲靴','Cushioned Boots','boots',(176,198,143)),
 'amber_bucket':('琥珀精华桶','Amber Essence Bucket','bucket',(232,181,74)),
 'azure_bucket':('蔚蓝精华桶','Azure Essence Bucket','bucket',(74,184,220)),
}
for name,(cn,en,kind,color) in items.items():
 pix=[(0,0,0,0)]*256
 for y in range(16):
  for x in range(16):
   inside=False
   if kind=='biscuit':inside=(x-7.5)**2+(y-7.5)**2<43
   elif kind=='bottle':inside=(5<=x<=10 and 1<=y<=4) or (3<=x<=12 and 5<=y<=13)
   elif kind=='wand':inside=abs(x+y-15)<=1 and 3<=x<=12 or (x-11)**2+(y-4)**2<9
   elif kind=='harness':inside=(3<=x<=12 and y in [3,4,11,12]) or (x in [3,4,11,12] and 4<=y<=11) or (7<=x<=8 and 5<=y<=10)
   elif kind=='cloth':inside=2<=x<=13 and 3<=y<=12
   elif kind=='wing':inside=2<=y<=11 and abs(x-7.5)<=min(7,y*0.8) and (y<8 or abs(x-7.5)<2)
   elif kind=='hood':inside=3<=x<=12 and 2<=y<=12 and not (5<=x<=10 and 6<=y<=12)
   elif kind=='vest':inside=3<=x<=12 and 2<=y<=13 and not (6<=x<=9 and y<=4)
   elif kind=='trousers':inside=3<=x<=12 and 2<=y<=13 and not (6<=x<=9 and y>=6)
   elif kind=='boots':inside=(3<=x<=6 or 9<=x<=12) and 3<=y<=12 or 2<=x<=13 and 11<=y<=13
   elif kind=='bucket':inside=(y in [3,4] and 4<=x<=11) or (5<=y<=12 and 3+(y-5)//5<=x<=12-(y-5)//5)
   if inside:
    shade=0.7 if (x+y)%5==0 else 1.08 if x<7 and y<7 else 0.92
    c=tuple(min(255,int(v*shade)) for v in color)
    if kind=='biscuit' and (x*3+y*5)%13==0:c=(104,79,61)
    if kind=='bucket' and (y<=4 or x in [3,12] or y==12):c=(122,146,155)
    pix[y*16+x]=c+(255,)
 png(Path('assets/magnitude/textures/item')/(name+'.png'),pix)
 put(Path('assets/magnitude/models/item')/(name+'.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'magnitude:item/'+name}})
 put(Path('assets/magnitude/items')/(name+'.json'),{'model':{'type':'minecraft:model','model':'magnitude:item/'+name}})
blocks={'attunement_plate':('调谐台','Attunement Plate','gold_block'), 'kinetic_pad':('微型动力垫','Kinetic Pad','copper_block'), 'amber_basin':('琥珀精华盆','Amber Basin','gold_block'), 'azure_basin':('蔚蓝精华盆','Azure Basin','lapis_block')}
for name,(cn,en,texture) in blocks.items():
 elements=[{'from':[0,0,0],'to':[16,4,16],'faces':{side:{'texture':'#surface'} for side in ['up','down','north','south','east','west']}}]
 if 'basin' in name:
  elements=[]
  for a,b in [([0,0,0],[16,2,16]),([0,2,0],[2,16,16]),([14,2,0],[16,16,16]),([2,2,0],[14,16,2]),([2,2,14],[14,16,16]),([2,9,2],[14,10,14])]:elements.append({'from':a,'to':b,'faces':{side:{'texture':'#surface'} for side in ['up','down','north','south','east','west']}})
 put(Path('assets/magnitude/models/block')/(name+'.json'),{'textures':{'surface':'minecraft:block/'+texture,'particle':'minecraft:block/'+texture},'elements':elements})
 put(Path('assets/magnitude/blockstates')/(name+'.json'),{'variants':{'':{'model':'magnitude:block/'+name}}})
 put(Path('assets/magnitude/models/item')/(name+'.json'),{'parent':'magnitude:block/'+name})
 put(Path('assets/magnitude/items')/(name+'.json'),{'model':{'type':'minecraft:model','model':'magnitude:item/'+name}})
 put(Path('data/magnitude/loot_table/blocks')/(name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'magnitude:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for name in ['amber_pool','azure_pool']:put(Path('assets/magnitude/blockstates')/(name+'.json'),{'variants':{'':{'model':'minecraft:block/water'}}})
put(Path('data/minecraft/tags/fluid/water.json'),{'replace':False,'values':['magnitude:'+n for n in ['amber_source','amber_flow','azure_source','azure_flow']]})
put(Path('data/magnitude/tags/block/protected.json'),{'replace':False,'values':['minecraft:bedrock','minecraft:barrier','minecraft:command_block','minecraft:chain_command_block','minecraft:repeating_command_block','minecraft:structure_block','minecraft:jigsaw','minecraft:end_portal','minecraft:end_portal_frame','minecraft:nether_portal','minecraft:reinforced_deepslate','#minecraft:beds']})
# Native 26.3 crafting and brewing recipes.
recipes={
 'amber_biscuit':(['minecraft:wheat','minecraft:honey_bottle','minecraft:glowstone_dust'],2),
 'azure_biscuit':(['minecraft:wheat','minecraft:sugar','minecraft:amethyst_shard'],2),
 'balance_biscuit':(['minecraft:wheat','minecraft:milk_bucket','minecraft:sugar'],2),
 'essence_reservoir':(['minecraft:glass_bottle','minecraft:copper_ingot','minecraft:amethyst_shard'],1),
 'tuning_wand':(['minecraft:stick','minecraft:amethyst_shard','minecraft:redstone'],1),
 'refraction_wand':(['magnitude:tuning_wand','minecraft:spyglass','minecraft:quartz'],1),
 'rescue_harness':(['minecraft:lead','minecraft:leather','minecraft:string'],1),
 'travel_blanket':(['minecraft:white_wool','minecraft:leather','minecraft:string'],1),
 'silk_wing':(['minecraft:paper','minecraft:paper','minecraft:string'],1),
 'amber_bucket':(['minecraft:water_bucket','magnitude:amber_biscuit'],1),
 'azure_bucket':(['minecraft:water_bucket','magnitude:azure_biscuit'],1),
 'amber_basin':(['minecraft:cauldron','magnitude:amber_bucket'],1),
 'azure_basin':(['minecraft:cauldron','magnitude:azure_bucket'],1),
 'attunement_plate':(['minecraft:iron_block','minecraft:redstone','minecraft:amethyst_shard'],1),
 'kinetic_pad':(['minecraft:copper_block','minecraft:redstone','minecraft:slime_ball'],1),
 'woven_hood':(['minecraft:leather_helmet','minecraft:vine'],1),
 'woven_vest':(['minecraft:leather_chestplate','minecraft:vine'],1),
 'woven_trousers':(['minecraft:leather_leggings','minecraft:vine'],1),
 'cushioned_boots':(['minecraft:leather_boots','minecraft:feather'],1),
}
for name,(ingredients,count) in recipes.items():put(Path('data/magnitude/recipe')/(name+'.json'),{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ingredients,'result':{'id':'magnitude:'+name,'count':count}})
for base,reagent in [('expansion','magnitude:amber_biscuit'),('contraction','magnitude:azure_biscuit'),('ascent','minecraft:honey_bottle'),('descent','minecraft:amethyst_shard')]:
 for item in ['potion','splash_potion','lingering_potion']:
  for tier in [1,2]:
   source='minecraft:awkward' if tier==1 else 'magnitude:'+base+'_1'
   input_reagent=reagent if tier==1 else 'minecraft:glowstone_dust'
   put(Path('data/magnitude/recipe/brewing')/(base+'_'+str(tier)+'_'+item+'.json'),{'type':'minecraft:brewing','input':{'item':'minecraft:'+item,'potion_contents':{'potions':source}},'reagent':{'item':input_reagent},'output':{'id':'minecraft:'+item,'components':{'minecraft:potion_contents':{'potion':'magnitude:'+base+'_'+str(tier)}}}})
# Independent language, no imported resource text.
cn={'tab.magnitude.equipment':'Magnitude · 尺寸交互','key.category.magnitude.controls':'Magnitude','key.categories.magnitude.controls':'Magnitude'}
en={'tab.magnitude.equipment':'Magnitude · Size Adventures','key.category.magnitude.controls':'Magnitude','key.categories.magnitude.controls':'Magnitude'}
for n,(c,e,*_) in items.items():cn['item.magnitude.'+n]=c;en['item.magnitude.'+n]=e
for n,(c,e,_) in blocks.items():cn['block.magnitude.'+n]=c;en['block.magnitude.'+n]=e
for n,c,e in [('enlarge','扩张','Enlargement'),('reduce','缩减','Reduction'),('ascent','持续扩张','Ascent'),('descent','持续缩减','Descent')]:cn['effect.magnitude.'+n]=c;en['effect.magnitude.'+n]=e
keys={'observe':('按住观察放大','Hold observation zoom'),'observe_toggle':('切换观察放大','Toggle observation zoom'),'magnify':('增加放大倍率','Increase magnification'),'reduce_zoom':('降低放大倍率','Decrease magnification'),'reset_view':('重置观察参数','Reset observation'),'smooth':('切换平滑观察','Toggle smooth observation'),'names':('切换名牌显示','Toggle nameplates'),'precision_up':('增加观察灵敏度','Increase zoom sensitivity'),'precision_down':('降低观察灵敏度','Decrease zoom sensitivity'),'blow':('吹风','Blow'),'stomp':('踏地','Stomp'),'release':('放下乘客','Release passenger'),'throw':('抛出乘客','Throw passenger'),'ability':('使用坐骑能力','Mount ability'),'ride':('骑乘瞄准目标','Ride aimed target')}
for n,(c,e) in keys.items():cn['key.magnitude.'+n]=c;en['key.magnitude.'+n]=e
messages={
 'help':('使用 /magnitude get、set、reset、consent、tool、random；详见玩法文档。','Use /magnitude get, set, reset, consent, tool or random. See the play guide.'),
 'size':('当前倍率 %s；目标 %s；碰撞高度 %s 格','Current scale %s; target %s; hitbox height %s blocks'),
 'reset':('尺寸和交互状态已重置','Size and interactions reset'),
 'consent':('外部交互许可已更新','Interaction consent updated'),
 'terrain':('地形破坏开关已更新；需服务端与个人开关同时开启','Terrain policy updated; server and personal switches must both be enabled'),
 'changed':('尺寸目标已更新','Size target updated'),
 'carry_mode':('携带位置已更新','Carrying position updated'),
 'tool':('工具参数已更新','Tool settings updated'),
 'random':('随机尺寸变化已更新','Random sizing updated'),
 'reloaded':('服务端配置已重新加载','Server configuration reloaded'),
 'food':('食物尺寸规则已保存','Food size rule saved'),
 'denied':('操作未执行：检查许可、体型、目标和冷却','Action refused: check consent, size ratio, target and cooldown'),
 'bound':('已绑定：%s','Bound to %s'),
 'tool_mode':('调谐杖模式：%s（0 乘法 / 1 加法 / 2 设定 / 3 交换 / 4 转移）','Tuning mode %s (0 multiply / 1 add / 2 set / 3 swap / 4 transfer)'),
 'target_missing':('绑定目标当前未加载或不在本维度','Bound target is unloaded or in another dimension'),
 'rest_failed':('此处不能睡眠；请靠近正常可用的床','Cannot rest here; use a valid nearby bed'),
}
for n,(c,e) in messages.items():cn['message.magnitude.'+n]=c;en['message.magnitude.'+n]=e
hints={
 'reservoir':('对实体使用抽取或注入；对空气使用作用于自己','Use on an entity to extract or inject; use in air for yourself'),
 'tuner':('对实体绑定；对空气操作；潜行使用切换模式','Use on entity to bind; use in air to apply; sneak-use to change mode'),
 'beam':('射线扩张目标；潜行时缩减（最远 32 格）','Enlarge aimed target; sneak to reduce (up to 32 blocks)'),
 'harness':('携带较小实体；对空气放下；潜行抛出','Carry a smaller entity; use in air to release; sneak to throw'),
 'rest':('微型旅行者的睡眠辅助，需已有的床','Resting aid for small travelers; requires an existing bed'),
 'charge':('储存尺寸：%s','Stored size: %s'),
 'operation':('模式 %s；参数 %s','Mode %s; value %s'),
}
for n,(c,e) in hints.items():cn['hint.magnitude.'+n]=c;en['hint.magnitude.'+n]=e
for base,label,english in [('expansion','扩张','Expansion'),('contraction','缩减','Contraction'),('ascent','持续扩张','Ascent'),('descent','持续缩减','Descent')]:
 for tier in [1,2]:
  for item,label_suffix,english_prefix in [('potion','药水','Potion of '),('splash_potion','喷溅药水','Splash Potion of '),('lingering_potion','滞留药水','Lingering Potion of '),('tipped_arrow','之箭','Arrow of ')]:
   key='item.minecraft.'+item+'.effect.magnitude.'+base+'_'+str(tier)
   cn[key]=label+label_suffix+(' II' if tier==2 else '');en[key]=english_prefix+english+(' II' if tier==2 else '')
put(Path('assets/magnitude/lang/zh_cn.json'),cn);put(Path('assets/magnitude/lang/en_us.json'),en)
print('Generated',len(items),'original icons;',len(recipes)+24,'recipes; bilingual resources')
