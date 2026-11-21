package com.dumalki.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

/**
 * Hosts the games (assets/index.html) in a full-screen WebView and gives the
 * page a native Russian text-to-speech voice, because Android WebView has no
 * speechSynthesis of its own.
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#E6F2FA"));
        w.setNavigationBarColor(Color.parseColor("#E6F2FA"));
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        tts = new TextToSpeech(this, this);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#E6F2FA"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient()); // keep navigation inside the app
        web.addJavascriptInterface(new Speaker(), "AndroidTTS");
        setContentView(web);

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        int r = tts.setLanguage(new Locale("ru", "RU"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            return; // no Russian voice on this phone: games still work, just silently
        }
        tts.setSpeechRate(0.92f);
        tts.setPitch(1.15f);
        ttsReady = true;
    }

    /** Exposed to JavaScript as window.AndroidTTS */
    class Speaker {
        @JavascriptInterface
        public void speak(String text) {
            if (ttsReady && text != null) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dumalki");
            }
        }

        @JavascriptInterface
        public void stop() {
            if (ttsReady) tts.stop();
        }

        @JavascriptInterface
        public boolean available() {
            return ttsReady;
        }
    }

    @Override
    public void onBackPressed() {
        // Let the page go back to the game list first; leave the app only from there.
        web.evaluateJavascript("window.dumalkiBack ? window.dumalkiBack() : false",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String handled) {
                        if (!"true".equals(handled)) MainActivity.super.onBackPressed();
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ttsReady) tts.stop();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
