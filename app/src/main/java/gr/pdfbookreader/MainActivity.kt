package gr.pdfbookreader

import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private val lib by lazy { getSharedPreferences("library2", MODE_PRIVATE) }
    private val progress by lazy { getSharedPreferences("progress", MODE_PRIVATE) }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        val id = UUID.randomUUID().toString()
        val ids = lib.getStringSet("ids", emptySet())!!.toMutableSet()
        ids.add(id)
        lib.edit().putStringSet("ids", ids).putString("uri_$id", uri.toString()).putString("name_$id", fileName(uri)).apply()
        showBooks()
        openBook(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }
    override fun onResume() { super.onResume(); if (::list.isInitialized) showBooks() }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,48,32,20) }
        root.addView(TextView(this).apply { text="Η βιβλιοθήκη μου"; textSize=30f; setTypeface(typeface,1) })
        root.addView(TextView(this).apply { text="Τα βιβλία σου, πάντα εκεί που σταμάτησες"; textSize=15f; setPadding(0,4,0,20) })
        root.addView(Button(this).apply { text="+  Προσθήκη PDF"; isAllCaps=false; setOnClickListener { picker.launch(arrayOf("application/pdf")) } })
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root); showBooks()
    }

    private fun showBooks() {
        list.removeAllViews()
        val ids=lib.getStringSet("ids", emptySet())!!.toList()
        if(ids.isEmpty()) {
            list.addView(TextView(this).apply { text="📚\n\nΗ βιβλιοθήκη είναι άδεια\nΠρόσθεσε το πρώτο σου PDF."; textSize=19f; gravity=Gravity.CENTER; setPadding(20,100,20,20) })
            return
        }
        ids.forEach { id ->
            val name=lib.getString("name_$id","PDF")!!
            val uri=lib.getString("uri_$id","")!!
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,12,0,12); setOnClickListener { openBook(id) }; setOnLongClickListener { bookMenu(id); true } }
            val cover=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.LTGRAY) }
            row.addView(cover, LinearLayout.LayoutParams(150,200))
            val pg=progress.getInt(uri,0)+1
            row.addView(TextView(this).apply { text="$name\n\nΣυνέχεια από σελίδα $pg"; textSize=17f; setPadding(22,0,8,0) }, LinearLayout.LayoutParams(0,-1,1f))
            list.addView(row)
            loadCover(uri,cover)
        }
    }

    private fun loadCover(uri:String, view:ImageView) {
        Thread {
            try {
                val fd:ParcelFileDescriptor=contentResolver.openFileDescriptor(Uri.parse(uri),"r") ?: return@Thread
                val r=PdfRenderer(fd)
                if(r.pageCount>0) {
                    val p=r.openPage(0); val w=240; val h=(w*p.height.toFloat()/p.width).toInt()
                    val bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); bm.eraseColor(Color.WHITE)
                    p.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close()
                    runOnUiThread { view.setImageBitmap(bm) }
                }
                r.close(); fd.close()
            } catch (_:Exception) {}
        }.start()
    }

    private fun bookMenu(id:String) {
        AlertDialog.Builder(this).setTitle(lib.getString("name_$id","Βιβλίο")).setItems(arrayOf("Μετονομασία","Διαγραφή")) { _, which ->
            if(which==0) {
                val e=EditText(this).apply { setText(lib.getString("name_$id","")) }
                AlertDialog.Builder(this).setTitle("Νέος τίτλος").setView(e).setPositiveButton("Αποθήκευση") { _,_-> lib.edit().putString("name_$id",e.text.toString()).apply(); showBooks() }.setNegativeButton("Άκυρο",null).show()
            } else {
                AlertDialog.Builder(this).setMessage("Να αφαιρεθεί από τη βιβλιοθήκη;").setPositiveButton("Διαγραφή") { _,_->
                    val ids=lib.getStringSet("ids",emptySet())!!.toMutableSet(); ids.remove(id)
                    lib.edit().putStringSet("ids",ids).remove("uri_$id").remove("name_$id").apply(); showBooks()
                }.setNegativeButton("Άκυρο",null).show()
            }
        }.show()
    }

    private fun openBook(id:String) { startActivity(Intent(this,ReaderActivity::class.java).putExtra("uri",lib.getString("uri_$id","")).putExtra("name",lib.getString("name_$id","PDF"))) }
    private fun fileName(uri:Uri):String {
        var n="Βιβλίο.pdf"
        contentResolver.query(uri,null,null,null,null)?.use { c -> if(c.moveToFirst()) { val i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(i>=0)n=c.getString(i) } }
        return n.removeSuffix(".pdf")
    }
}