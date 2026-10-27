package io.github.ledflighter.microimg;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class SmokeInstrumentation extends Instrumentation
{
    private int passed;
    private WebView webView;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart()
    {
        Bundle result = new Bundle();
        try
        {
            for(int size : new int[] {1, 15, 252, 253, 858})
            {
                char[] characters = new char[size];
                Arrays.fill(characters, 'A');
                String text = new String(characters);
                NdefMessage message = NdefText.message(text);
                check(message.toByteArray().length == size + (size <= 252 ? 7 : 10), "NDEF overhead at " + size);
            }
            String text = "P1/1#DP:W2H2:0AF1";
            check(NdefText.read(new NdefMessage(NdefText.message(text).toByteArray())).equals(text), "NDEF serialize/read round trip");
            byte[] language = "de".getBytes(StandardCharsets.US_ASCII), utf16 = text.getBytes(StandardCharsets.UTF_16);
            byte[] payload = new byte[3 + utf16.length]; payload[0] = (byte)0x82;
            System.arraycopy(language, 0, payload, 1, 2); System.arraycopy(utf16, 0, payload, 3, utf16.length);
            NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);
            check(NdefText.read(new NdefMessage(new NdefRecord[] {record})).equals(text), "UTF-16 NDEF text");
            check(NdefText.read(new NdefMessage(new NdefRecord[] {NdefRecord.createMime("text/plain", text.getBytes(StandardCharsets.UTF_8))})).equals(text), "MIME text NDEF");
            try { NdefText.read(NdefText.message("unrelated data")); throw new AssertionError("Unrelated record accepted"); }
            catch(java.io.IOException expected) { passed++; }
            record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], new byte[] {63, 1});
            try { NdefText.read(new NdefMessage(new NdefRecord[] {record})); throw new AssertionError("Malformed record accepted"); }
            catch(java.io.IOException expected) { passed++; }

            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity = startActivitySync(intent);
            runOnMainSync(() -> webView = (WebView)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0));
            long deadline = System.currentTimeMillis() + 30000;
            while(!"true".equals(js("!!window.MicroCodec && typeof window.onNativeEvent === 'function'")))
            {
                if(System.currentTimeMillis() > deadline) throw new AssertionError("App JavaScript did not initialize");
                Thread.sleep(200);
            }
            check("true".equals(js("document.getElementById('read').disabled")), "NFC is disabled on emulator without NFC");
            check("true".equals(js("document.getElementById('demo').click(); chunks.length > 0 && stream.startsWith('ZIP:')")), "Demo compression");
            check("true".equals(js("codec.decode(stream).width === 64 && codec.decode(stream).height === 48")), "Preview dimensions");
            check("true".equals(js("chunks.every(c => codec.ndefSize(c) <= 868)")), "Native default capacity");
            check("true".equals(js("document.getElementById('raw').value = chunks.slice().reverse().join('\\n'); document.getElementById('decode').click(); !document.getElementById('decodedCard').hidden && decodedImage.width === 64")), "Manual out-of-order reconstruction");
            check("true".equals(js("document.getElementById('preview').toDataURL() === document.getElementById('decoded').toDataURL()")), "Preview and reconstruction are pixel identical");
            check("true".equals(js("document.getElementById('reset').click(); document.getElementById('decodedCard').hidden && collector.parts.size === 0")), "Reset collection");
            check("true".equals(js("document.getElementById('format').value = 'G4'; render(); codec.decode(stream).width === 64")), "G4 mode");
            check("true".equals(js("document.getElementById('format').value = 'DP'; render(); codec.decode(stream).pixels.some(v => v > 9)")), "Deep-PX hexadecimal values");
            check("true".equals(js("document.getElementById('rotate').click(); codec.decode(stream).width === 48 && codec.decode(stream).height === 64")), "Rotate and preserve aspect");
            check("true".equals(js("document.getElementById('format').value = 'ZIP'; document.getElementById('capacity').value = '137'; render(); chunks.every(c => codec.ndefSize(c) <= 137)")), "Small tag capacity");
            check("true".equals(js("document.getElementById('raw').value = 'P1/2#ZIP:incomplete'; document.getElementById('decode').click(); collector.parts.size === 1 && !collector.complete")), "Partial scan survives for continuation");
            check("true".equals(js("window.onNativeEvent({type:'hardware',available:true,enabled:true}); document.getElementById('write').click(); !document.getElementById('confirm').hidden")), "Write requires explicit confirmation");
            check("true".equals(js("document.getElementById('dismissConfirm').click(); document.getElementById('confirm').hidden")), "Cancel confirmation");
            check("true".equals(js("window.onNativeEvent({type:'reading',message:'test'}); document.getElementById('editor').disabled && !document.getElementById('operation').hidden")), "Active NFC freezes editor");
            check("true".equals(js("window.onNativeEvent({type:'cancelled',message:'test'}); !document.getElementById('editor').disabled && document.getElementById('operation').hidden")), "Cancel unlocks editor");
            check("true".equals(js("window.savedCode = stream; window.savedPreview = document.getElementById('preview').toDataURL(); loadCode(chunks.slice().reverse().join('\\n'), 'saved.txt'); source === null && importedImage !== null && stream === window.savedCode")), "Saved chunks import without re-encoding");
            check("true".equals(js("document.getElementById('imageSettings').disabled && !document.getElementById('openImport').disabled && document.getElementById('preview').toDataURL() === window.savedPreview")), "Imported preview and source controls");
            check("true".equals(js("document.getElementById('capacity').value='868'; render(); stream === window.savedCode && codec.importText(chunks.join('\\n')).stream === window.savedCode")), "Change capacity preserves imported image data");
            check("true".equals(js("document.getElementById('write').click(); stream === window.savedCode && !document.getElementById('confirm').hidden")), "Imported data survives write preparation");
            check("true".equals(js("document.getElementById('dismissConfirm').click(); try { loadCode('P1/2#ZIP:broken'); } catch(error) {} stream === window.savedCode")), "Bad import leaves previous image intact");
            check("true".equals(js("document.getElementById('openImport').click(); !document.getElementById('codeImport').hidden && document.getElementById('codeText').tagName === 'TEXTAREA' && document.getElementById('codeInput') === null")), "Import opens a paste dialog without a text file picker");
            check("true".equals(js("window.dialogBounds=document.getElementById('codeImport').getBoundingClientRect(); window.dialogBounds.top === 0 && window.dialogBounds.left === 0 && Math.abs(window.dialogBounds.bottom-innerHeight) < 1 && Math.abs(window.dialogBounds.right-innerWidth) < 1")), "Import dialog stays inside the viewport on older WebViews");
            check("true".equals(js("document.getElementById('codeText').value='P1/2#ZIP:broken'; document.getElementById('applyImport').click(); !document.getElementById('codeImport').hidden && !document.getElementById('importError').hidden && stream === window.savedCode")), "Invalid paste preserves the image and reports error in dialog");
            js("document.getElementById('codeText').value=''; document.getElementById('codeText').focus(); true");
            runOnMainSync(() -> ((ClipboardManager)getTargetContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("MicroIMG test", "P1/1#DP:W2H2:0AF1")));
            js("document.getElementById('pasteImport').click(); true");
            deadline = System.currentTimeMillis() + 10000;
            while(!"true".equals(js("document.getElementById('codeText').value === 'P1/1#DP:W2H2:0AF1'")))
            {
                if(System.currentTimeMillis() > deadline) throw new AssertionError("Android clipboard paste timed out");
                Thread.sleep(100);
            }
            check("true".equals(js("document.getElementById('codeText').value === 'P1/1#DP:W2H2:0AF1'")), "Android clipboard pastes into the import text field");
            check("true".equals(js("document.getElementById('applyImport').click(); document.getElementById('codeImport').hidden && stream === 'DP:W2H2:0AF1' && importedImage.image.width === 2")), "Pasted text imports without re-encoding");
            check("true".equals(js("document.getElementById('openImport').click(); document.getElementById('codeText').value='DP:W1H1:A'; document.getElementById('dismissImport').click(); stream === 'DP:W2H2:0AF1'")), "Cancel paste keeps the existing image");
            check("true".equals(js("document.getElementById('demo').click(); importedImage === null && source !== null && !document.getElementById('imageSettings').disabled")), "Opening raster restores editable settings");
            check("true".equals(js("document.getElementById('levels').type === 'range' && document.getElementById('levels').min === '2' && document.getElementById('levels').max === '16'")), "Grayscale slider covers every level from 2 to 16");
            check("true".equals(js("document.getElementById('levels').value='7'; document.getElementById('levels').dispatchEvent(new Event('input')); render(); document.getElementById('levelsValue').textContent === '7' && codec.decode(stream).pixels.every(v => v < 7)")), "Grayscale slider updates label and actual pixel quantization");
            check("true".equals(js("document.getElementById('exportPreview').click(); !document.getElementById('pngExport').hidden && document.getElementById('exportScale').type === 'number'")), "Preview export offers a free scale factor");
            check("true".equals(js("window.dialogBounds=document.getElementById('pngExport').getBoundingClientRect(); window.dialogBounds.top === 0 && Math.abs(window.dialogBounds.bottom-innerHeight) < 1")), "Export dialog stays inside the viewport on older WebViews");
            check("true".equals(js("document.getElementById('exportScale').value='13'; document.getElementById('exportScale').dispatchEvent(new Event('input')); document.getElementById('exportSize').textContent === '832 × 624 Pixel' && !document.getElementById('confirmExport').disabled")), "Custom factor thirteen produces exact preview dimensions");
            check("true".equals(js("['', '0', '-1', '1.5', '1000000'].every(value => { document.getElementById('exportScale').value=value; updateExportSize(); return document.getElementById('confirmExport').disabled && !document.getElementById('exportError').hidden; })")), "Invalid or oversized export factors are rejected without silent clamping");
            check("true".equals(js("document.getElementById('exportScale').value='13'; updateExportSize(); window.originalSave=save; window.exportedPng=null; save=(name,mime,base64) => { window.exportedPng={name,mime,base64}; }; try { document.getElementById('confirmExport').click(); } finally { save=window.originalSave; } document.getElementById('pngExport').hidden && window.exportedPng.name === 'MicroIMG_832x624.png'")), "Custom scale generates a PNG through the export action");
            check("true".equals(js("window.pngBytes=Uint8Array.from(atob(window.exportedPng.base64), c => c.charCodeAt(0)); window.pngHeader=new DataView(window.pngBytes.buffer); window.pngHeader.getUint32(16) === 832 && window.pngHeader.getUint32(20) === 624")), "Generated PNG header contains the requested dimensions");
            check("true".equals(js("collector=new codec.Collector(); updateCollection(); setOperation('read','test'); window.onNativeEvent({type:'chunk',text:'P1/1#DP:W2H2:0AF1'}); activeMode === 'read' && collector.complete && collector.total === 1 && document.getElementById('reset').hidden && decodedImage.width === 2")), "Single-tag image keeps NFC reading active and hides reset");
            check("true".equals(js("document.getElementById('saveDecoded').click(); document.getElementById('exportScale').value='13'; updateExportSize(); exportTarget.width === 2 && document.getElementById('exportSize').textContent === '26 × 26 Pixel'")), "Decoded export also accepts a free scale factor");
            check("true".equals(js("window.onNativeEvent({type:'chunk',text:'P1/1#DP:W3H2:012345'}); decodedImage.width === 3 && collector.lines() === 'P1/1#DP:W3H2:012345' && activeMode === 'read' && document.getElementById('reset').hidden")), "Next single-tag image replaces the previous collection without reset");
            check("true".equals(js("exportTarget.width === 2 && exportTarget.height === 2 && document.getElementById('exportSize').textContent === '26 × 26 Pixel'")), "Export snapshot stays on the selected image during subsequent scans");
            check("true".equals(js("document.getElementById('dismissExport').click(); window.onNativeEvent({type:'chunk',text:'P1/1#ZIP:AAAA'}); decodedImage.width === 3 && collector.lines() === 'P1/1#DP:W3H2:012345' && activeMode === 'read'")), "Bad single-tag scan preserves the prior image and active reader");
            check("true".equals(js("window.onNativeEvent({type:'cancelled',message:'test'}); activeMode === null && document.getElementById('reset').hidden && !document.getElementById('read').disabled")), "Single-tag read can be started again after stopping");
            check("true".equals(js("setOperation('read','test'); window.onNativeEvent({type:'chunk',text:'P2/2#0AF1'}); collector.parts.size === 1 && collector.total === 2 && !document.getElementById('reset').hidden && activeMode === 'read'")), "Switch from a single-tag image to an unordered multi-tag image");
            check("true".equals(js("window.onNativeEvent({type:'chunk',text:'P1/2#DP:W2H2:'}); decodedImage.width === 2 && collector.complete && collector.total === 2 && activeMode === null && !document.getElementById('reset').hidden")), "Multi-tag completion retains its explicit reset workflow");
            check("true".equals(js("document.getElementById('read').click(); collector.complete && collector.total === 2 && activeMode === null")), "Completed multi-tag series does not silently mix with a new picture");
            check("true".equals(js("setTheme('dark'); document.getElementById('theme').click(); document.documentElement.dataset.theme === 'light' && localStorage.getItem('microimgTheme') === 'light' && getComputedStyle(document.body).backgroundColor === 'rgb(248, 250, 252)'")), "Original light theme and persistence");
            check("true".equals(js("document.getElementById('theme').click(); document.documentElement.dataset.theme === 'dark' && getComputedStyle(document.body).backgroundColor === 'rgb(11, 15, 26)'")), "Original dark theme");
            check("true".equals(js("document.getElementById('reset').click(); document.getElementById('format').value='ZIP'; document.getElementById('capacity').value='868'; document.getElementById('rotate').click(); document.getElementById('createTab').click(); document.documentElement.scrollTop=0; true")), "Restore screen for inspection");
            result.putString("stream", "PASS: " + passed + " Android NDEF and UI checks\n");
            result.putInt("passed", passed);
            finish(Activity.RESULT_OK, result);
        }
        catch(Throwable error)
        {
            result.putString("stream", "FAIL after " + passed + " checks: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void check(boolean condition, String message)
    {
        if(!condition) throw new AssertionError(message);
        passed++;
    }
    private String js(String script) throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        runOnMainSync(() -> webView.evaluateJavascript(script, result -> { value.set(result); latch.countDown(); }));
        if(!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("WebView evaluation timed out");
        return value.get();
    }
}
