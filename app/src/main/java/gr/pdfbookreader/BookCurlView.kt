package gr.pdfbookreader

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class BookCurlView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):View(context,attrs){
private var left:Bitmap?=null;private var right:Bitmap?=null;private var next:Bitmap?=null;private var drag=0f;private var active=false;private var startX=0f;var onTurnNext:(()->Unit)?=null
fun setPages(l:Bitmap?,r:Bitmap?,n:Bitmap?){left=l;right=r;next=n;drag=0f;invalidate()}
override fun onDraw(c:Canvas){super.onDraw(c);val mid=width/2f;val h=height.toFloat();c.drawColor(Color.rgb(22,22,22));left?.let{drawFit(c,it,0f,0f,mid,h)};next?.let{drawFit(c,it,mid,0f,width.toFloat(),h)};val fold=(width-drag).coerceIn(mid,width.toFloat());right?.let{b->c.save();c.clipRect(mid,0f,fold,h);drawFit(c,b,mid,0f,width.toFloat(),h);c.restore();if(active&&fold<width){val strip=min(70f,width-fold);val p=Paint(Paint.ANTI_ALIAS_FLAG);p.shader=LinearGradient(fold-strip,0f,fold+strip,0f,intArrayOf(0x33000000,0x99FFFFFF.toInt(),0x66000000),null,Shader.TileMode.CLAMP);c.drawRect(max(mid,fold-strip),0f,min(width.toFloat(),fold+strip),h,p)}};val spine=Paint(Paint.ANTI_ALIAS_FLAG);spine.shader=LinearGradient(mid-18,0f,mid+18,0f,intArrayOf(0x55000000,0x11000000,0x55000000),null,Shader.TileMode.CLAMP);c.drawRect(mid-18,0f,mid+18,h,spine)}
private fun drawFit(c:Canvas,b:Bitmap,l:Float,t:Float,r:Float,bot:Float){val sw=r-l;val sh=bot-t;val s=min(sw/b.width,sh/b.height);val w=b.width*s;val h=b.height*s;val x=l+(sw-w)/2;val y=t+(sh-h)/2;c.drawBitmap(b,null,RectF(x,y,x+w,y+h),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))}
override fun onTouchEvent(e:MotionEvent):Boolean{when(e.action){MotionEvent.ACTION_DOWN->{if(e.x<width*0.55f)return false;startX=e.x;active=true;parent?.requestDisallowInterceptTouchEvent(true);invalidate();return true};MotionEvent.ACTION_MOVE->{if(active){drag=(startX-e.x).coerceIn(0f,width/2f);invalidate();return true}};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(active){val complete=drag>width*0.16f;active=false;if(complete)onTurnNext?.invoke()else{drag=0f;invalidate()};return true}}};return true}
}