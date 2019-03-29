package com.tzutalin.dlibtest;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

public class Setting extends AppCompatActivity {

    private SharedPreferences mSharedPreferences;
    private Spinner mSpinner;
    private static int spinnerStatus = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        mSharedPreferences = getSharedPreferences("userInfo", MODE_PRIVATE);
        mSpinner = (Spinner) findViewById(R.id.spinner);
        mSpinner.setSelection(spinnerStatus);

        Button mSettingBt = (Button) findViewById(R.id.button);
        mSettingBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String selectedItem = mSpinner.getSelectedItem().toString();
                spinnerStatus = mSpinner.getSelectedItemPosition();
                String detectionMode = null;
                detectionMode = selectedItem;

                SharedPreferences.Editor edit = mSharedPreferences.edit();
                edit.putString("detectionMode", detectionMode);
                edit.commit();
                Toast.makeText(Setting.this, "Successfully saved", Toast.LENGTH_SHORT).show();
            }
        });

    }
}
