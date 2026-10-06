package gr.pdfbookreader

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.content.res.Configuration
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.min

class ReaderActivity : AppCompatActivity() {
 private var fd: ParcelFileDescriptor? = null
 private var renderer: PdfRenderer? = null
 private lateinit var image: ImageView
 private lateinit var spread: LinearLayout
 private lateinit var curl: BookCurlView
 private lateinit var leftPage: ImageView
 private lateinit var rightPage: ImageView
 private lateinit var bar: LinearLayout
 private lateinit var info: TextView
 private lateinit var seek: SeekBar
 private var page = 0
 private var downX = 0f
 private var downY = 0f
 private lateinit var key: String
 private val prefs by lazy { getSharedPreferences("progress", MODE_PRIVATE) }
 private val marks by lazy { getSharedPreferences("bookmarks", MODE_PRIVATE) }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = Color.BLACK
  val uri = Uri.parse(intent.getStringExtra("uri"))
  key = uri.toString()
  val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(20,20,20)) }
  bar = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(8,5,8,5) }
  bar.addView(Button(this).apply { text="‹"; textSize=24f; setOnClickListener { finish() } })
  info = TextView(this).apply { setTextColor(Color.WHITE); gravity=Gravity.CENTER }
  bar.addView(info, LinearLayout.LayoutParams(0,-2,1f))
  bar.addView(Button(this).apply { text="🔖"; setOnClickListener { toggleBookmark() } })
  bar.addView(Button(this).apply { text="⋮"; setOnClickListener { readerMenu() } })
  image = ImageView(this).apply { setBackgroundColor(Color.rgb(28,28,28)); scaleType=ImageView.ScaleType.FIT_CENTER }
  leftPage = ImageView(this).apply { setBackgroundColor(Color.rgb(28,28,28)); scaleType=ImageView.ScaleType.FIT_CENTER; setPadding(4,8,1,8) }
  rightPage = ImageView(this).apply { setBackgroundColor(Color.rgb(28,28,28)); scaleType=ImageView.ScaleType.FIT_CENTER; setPadding(1,8,4,8) }
  spread = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setBackgroundColor(Color.rgb(12,12,12)); addView(leftPage,LinearLayout.LayoutParams(0,-1,1f)); addView(rightPage,LinearLayout.LayoutParams(0,-1,1f)) }
  curl=BookCurlView(this).apply { onTurnNext={ next() } }
  seek = SeekBar(this).apply { setPadding(24,0,24,8) }
  root.addView(bar)
  if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) root.addView(curl,LinearLayout.LayoutParams(-1,0,1f))
  else root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(seek)
  setContentView(root)
  try {
   fd = contentResolver.openFileDescriptor(uri,"r")
   renderer = PdfRenderer(fd!!)
   page = prefs.getInt(key,0).coerceIn(0,(renderer!!.pageCount-1).coerceAtLeast(0))
   seek.max = (renderer!!.pageCount-1).coerceAtLeast(0); seek.progress=page
   seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if(fromUser){ page=p; render() } }
    override fun onStartTrackingTouch(s: SeekBar?) {}
    override fun onStopTrackingTouch(s: SeekBar?) {}
   })
   val touch=View.OnTouchListener { _,e ->
    when(e.action) {
     MotionEvent.ACTION_DOWN -> { downX=e.x; downY=e.y; true }
     MotionEvent.ACTION_UP -> {
      val dx=e.x-downX; val dy=e.y-downY
      if(abs(dx)>100 && abs(dx)>abs(dy)) { if(dx<0) next() else prev() }
      else if(abs(dx)<30 && abs(dy)<30) toggleControls()
      true
     }
     else -> true
    }
   }
   image.setOnTouchListener(touch)
   if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) curl.post { render() } else image.post { render() }
  } catch(e: Exception) { Toast.makeText(this,"Δεν ήταν δυνατό να ανοίξει το PDF",Toast.LENGTH_LONG).show(); finish() }
 }

 private fun render() {
  val r=renderer?:return; if(r.pageCount==0)return
  if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) {
   val left=if(page==0) 0 else if(page%2==0) page else page-1
   page=left
   val rw=(resources.displayMetrics.widthPixels*0.82).toInt().coerceAtLeast(700)
   val lb=renderBitmap(left,rw); val rb=if(left+1<r.pageCount)renderBitmap(left+1,rw)else null; val nb=if(left+3<r.pageCount)renderBitmap(left+3,rw)else null
   curl.setPages(lb,rb,nb)
   info.text=(intent.getStringExtra("name")?:"PDF")+"   "+(left+1)+"–"+min(left+2,r.pageCount)+" / "+r.pageCount
  } else {
   image.setImageBitmap(renderBitmap(page,min((resources.displayMetrics.widthPixels*1.7).toInt(),1800).coerceAtLeast(800)))
   info.text=(intent.getStringExtra("name")?:"PDF")+"   "+(page+1)+" / "+r.pageCount+(if(isMarked())"  🔖" else "")
  }
  seek.progress=page; prefs.edit().putInt(key,page).apply()
 }
 private fun renderBitmap(index:Int,w:Int):Bitmap {
  val p=renderer!!.openPage(index); val h=(w*p.height.toFloat()/p.width).toInt().coerceAtLeast(1)
  val bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); bm.eraseColor(Color.WHITE)
  p.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close(); return bm
 }
 private fun next(){ val step=if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)2 else 1; if(page<(renderer?.pageCount?:1)-1){ page=(page+step).coerceAtMost((renderer?.pageCount?:1)-1); render() } }
 private fun prev(){ val step=if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)2 else 1; if(page>0){ page=(page-step).coerceAtLeast(0); render() } }
 private fun toggleControls(){ val v=if(bar.visibility==View.VISIBLE)View.GONE else View.VISIBLE; bar.visibility=v; seek.visibility=v }
 private fun isMarked() = marks.getStringSet(key,emptySet())!!.contains(page.toString())
 private fun toggleBookmark(){ val s=marks.getStringSet(key,emptySet())!!.toMutableSet(); if(!s.add(page.toString()))s.remove(page.toString()); marks.edit().putStringSet(key,s).apply(); render() }
 private fun readerMenu(){ AlertDialog.Builder(this).setItems(arrayOf("Μετάβαση σε σελίδα","Σελιδοδείκτες","Πλήρης οθόνη")){_,w->when(w){0->goTo();1->showMarks();2->fullscreen()}}.show() }
 private fun goTo(){
  val e=EditText(this).apply { inputType=2; hint="1 - "+(renderer?.pageCount?:1) }
  AlertDialog.Builder(this).setTitle("Μετάβαση σε σελίδα").setView(e).setPositiveButton("Μετάβαση"){_,_->
   val p=(e.text.toString().toIntOrNull()?:1)-1; if(p in 0 until (renderer?.pageCount?:0)){ page=p; render() }
  }.setNegativeButton("Άκυρο",null).show()
 }
 private fun showMarks(){
  val ps=marks.getStringSet(key,emptySet())!!.mapNotNull{it.toIntOrNull()}.sorted()
  if(ps.isEmpty()){ Toast.makeText(this,"Δεν υπάρχουν σελιδοδείκτες",Toast.LENGTH_SHORT).show(); return }
  AlertDialog.Builder(this).setTitle("Σελιδοδείκτες").setItems(ps.map{"Σελίδα "+(it+1)}.toTypedArray()){_,i->page=ps[i];render()}.show()
 }
 private fun fullscreen(){ bar.visibility=View.GONE; seek.visibility=View.GONE; window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY }
 override fun onDestroy(){ renderer?.close(); fd?.close(); super.onDestroy() }
}