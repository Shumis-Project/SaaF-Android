package com.shumiproject.saaf.activities;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.drawable.ColorDrawable;
import android.widget.Button;
import android.widget.Toast;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import com.shumiproject.saaf.R;
import com.shumiproject.saaf.utils.RadioList;
import com.shumiproject.saaf.utils.OSW;
import com.shumiproject.saaf.adapters.RadioListAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.OnPermissionCallback;

public class MainActivity extends AppCompatActivity implements OnPermissionCallback {
    private ArrayList<RadioList> radio;
    private Button button;
    private RecyclerView recyclerView;
    private AlertDialog backPressedDialog, loading;
    private boolean canCloseFile;
    
    // Async things
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize(savedInstanceState);
        letsGo();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown the executor to prevent..... memory leaks?
        executor.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (canCloseFile) {
            new MaterialAlertDialogBuilder(this)
                .setCancelable(true)
                .setMessage("Do you want to close " + RadioList.stationName + " station?")
                .setNegativeButton("NO", null)
                .setPositiveButton("YES", (_which, _dialog) -> {
                    canCloseFile = false;
                    radio.clear();
                    recyclerView.getAdapter().notifyDataSetChanged();
                    recyclerView.setVisibility(View.GONE);
                    button.setVisibility(View.VISIBLE);
                    getSupportActionBar().setSubtitle(null);
                    
                    // Nullify static vars
                    // RadioList.stationLogo = null; // Can't nullify int
                    RadioList.stationName = null;
                })
                .show();
        } else {
            backPressedDialog.show();
        }
    }
    
    // If everything's sets, just start it
    private void letsGo () {
        if (XXPermissions.isGranted(this, Permission.MANAGE_EXTERNAL_STORAGE)) {
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(v -> {
                Intent intent = new Intent(this, FilePickerActivity.class);
                launcher.launch(intent);
            });
            
            // Don't call permission request if all permissions granted
            return;
        }
    
        // Otherwise, ask permission.
        XXPermissions.with(this)
            .permission(Permission.MANAGE_EXTERNAL_STORAGE)
            .request(this);
    }
    
    private void open(Intent intent) {
        // Show loading on first
        loading.show();
    
        executor.execute(() -> {
            // backend
            try {
                String path = intent.getStringExtra("path");
                String station = intent.getStringExtra("station");
                
                radio = RadioList.createList(this, path, station);
                RadioListAdapter adapter = new RadioListAdapter(radio);
                
                if (radio.isEmpty()) {
                    throw new Exception("Failed to open the file");
                }
                canCloseFile = true;
                
                handler.post(() -> {
                    // frontend
                    recyclerView.setAdapter(adapter);
                    recyclerView.setLayoutManager(new LinearLayoutManager(this));
                    recyclerView.setVisibility(View.VISIBLE);
                    button.setVisibility(View.GONE);
                    adapter.setCallback(new RadioListAdapter.Callback() {
                        @Override
                        public void onItemClicked(View view, RadioList radioList) {
                            menuDialog(radioList);
                        }
                        
                        @Override
                        public boolean onItemLongClicked(View view, RadioList radioList) {
                            menuDialog(radioList);
                            return true;
                        }
                    });
                    getSupportActionBar().setSubtitle(RadioList.stationName);
                    
                    // Dismiss loading if all done.
                    loading.dismiss();
                });
            } catch (Exception err) {
                handler.post(() -> {
                    Toast.makeText(this, "Error: " + err.getMessage(), Toast.LENGTH_LONG).show();
                    loading.dismiss();
                });
            }
        });
    }
    
    // Show menu when item clicked
    private void menuDialog (RadioList radioList) {
        final String[] menu = { "Play", "Extract", "Replace" };
        int logo = (RadioList.stationLogo != 0) ? RadioList.stationLogo : R.drawable.utp;
        
        new MaterialAlertDialogBuilder(this)
            .setTitle(radioList.getTitle())
            .setIcon(logo)
            .setItems(menu, (dialog, which) -> {
                switch(which) {
                    case 0:
                        // TODO
                    break;
                    case 1:
                        // TODO
                    break;
                    case 2:
                        Toast.makeText(this, "Coming soon...", Toast.LENGTH_LONG).show();
                    break;
                }
            })
            .show();
    }

    // Initialize some shit
    private void initialize(Bundle savedInstanceState) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        button = (Button) findViewById(R.id.button);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        backPressedDialog = new MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setNegativeButton("NO", null)
            .setMessage("Are you sure you want to close this app?")
            .setPositiveButton("YES", (_which, _dialog) -> finish())
            .create();
        loading = new MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setView(View.inflate(this, R.layout.loading, null))
            // Make the background transparent so it's only show loading
            .setBackground(new ColorDrawable(0))
            .create();

        // Someone said using "setHasFixedSize" can optimize the recyclerview.
        recyclerView.setHasFixedSize(true);
        recyclerView.setVisibility(View.GONE);
    }
    
    // Permission handler
    @Override
    public void onGranted(List<String> permissions, boolean all) {
        letsGo();
    }
    
    @Override
    public void onDenied(List<String> permissions, boolean never) {
        new MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setMessage("This app requires storage access to work properly. Please grant storage permission.")
            .setPositiveButton("OK", (_dialog, _which) -> {
                letsGo();
            })
            .show();
    }
    
    // activity launcher
    ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (ActivityResult result) -> {
        int resultCode = result.getResultCode();
        
        if (resultCode == AppCompatActivity.RESULT_OK) {
            Intent intentFilePicker = result.getData();
            open(intentFilePicker);
        }
    });
}