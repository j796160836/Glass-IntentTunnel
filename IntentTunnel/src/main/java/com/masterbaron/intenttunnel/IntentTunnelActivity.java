package com.masterbaron.intenttunnel;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.masterbaron.intenttunnel.glass.GlassConfigureActivity;
import com.masterbaron.intenttunnel.android.AndroidConfigureActivity;
import com.masterbaron.intenttunnel.router.RouterService;

/**
 * Created by Van Etten on 1/4/14.
 */
public class IntentTunnelActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(RouterService.isGlass() ) {
            startActivity(new Intent(this, GlassConfigureActivity.class ));
        } else {
            startActivity(new Intent(this, AndroidConfigureActivity.class ));
        }

        this.finish();
    }
}
