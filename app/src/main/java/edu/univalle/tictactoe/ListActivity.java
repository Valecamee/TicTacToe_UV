package edu.univalle.tictactoe;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class ListActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> serversAdapter;

    private ArrayList<String> serversList = new ArrayList<>();
    private ArrayList<String> devicesAddresses = new ArrayList<>();

    private BroadcastReceiver receiver;
    private TextView textScanStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        ListView listServers = findViewById(R.id.listServers);
        textScanStatus = findViewById(R.id.textScanStatus);

        serversAdapter = new ArrayAdapter<>(
                this,
                R.layout.list_item_server,
                R.id.textDeviceName,
                serversList
        );

        listServers.setAdapter(serversAdapter);

        listServers.setOnItemClickListener((parent, view, position, id) -> conectarServidor(position));

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        iniciarEscaneo();
    }

    private void iniciarEscaneo() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.permiso_requerido), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // Cancelar escaneo activo
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (Exception ignored) {}

        // Crear receiver
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {

                String action = intent.getAction();

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (ActivityCompat.checkSelfPermission(ListActivity.this,
                            Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) return;

                    String name = device.getName();
                    String address = device.getAddress();

                    // FILTRO: solo salas
                    if (name != null && name.startsWith("TICTACTOE_ROOM_")) {

                        if (!devicesAddresses.contains(address)) {

                            serversList.add(name + "\n" + address);
                            devicesAddresses.add(address);
                            serversAdapter.notifyDataSetChanged();
                        }
                    }

                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    textScanStatus.setText(getString(R.string.scan_status_complete));
                }
            }
        };

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, filter);

        bluetoothAdapter.startDiscovery();
        textScanStatus.setText(getString(R.string.scan_status_active));
    }

    private void conectarServidor(int position) {
        String address = devicesAddresses.get(position);

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED) {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            }
        } catch (Exception ignored) {}

        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("role", "CLIENT");
        intent.putExtra("address", address);
        startActivity(intent);
        finish();
    }

    // Botón volver en la lista
    public void goBack(android.view.View view) {
        finish();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (Exception ignored) {}

        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {}
    }
}
