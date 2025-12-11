package edu.univalle.tictactoe; // ⚠️ CAMBIA por tu package

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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class ListActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> serversAdapter;
    private ArrayList<String> serversList;
    private ArrayList<String> devicesAddresses;
    private TextView textScanStatus;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        // Inicializar vistas
        ListView listServers = findViewById(R.id.listServers);
        textScanStatus = findViewById(R.id.textScanStatus);

        // Inicializar listas
        serversList = new ArrayList<>();
        devicesAddresses = new ArrayList<>();

        // Configurar adaptador
        serversAdapter = new ArrayAdapter<>(this,
                R.layout.list_item_server, R.id.textDeviceName, serversList);
        listServers.setAdapter(serversAdapter);

        // Configurar click en items
        listServers.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                conectarServidor(position);
            }
        });

        // Obtener adaptador Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Iniciar búsqueda
        startDiscovery();
    }

    private void startDiscovery() {
        // Verificar permisos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this,
                            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permisos de Bluetooth necesarios",
                        Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // Cancelar búsqueda anterior si existe
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            return;
        }

        // Configurar receptor de broadcasts
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    try {
                        if (ActivityCompat.checkSelfPermission(ListActivity.this,
                                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {

                            String deviceName = device.getName();
                            String deviceAddress = device.getAddress();

                            if (deviceName == null) {
                                deviceName = "Unknown Device";
                            }

                            String deviceInfo = deviceName + "\n" + deviceAddress;

                            if (!devicesAddresses.contains(deviceAddress)) {
                                serversList.add(deviceInfo);
                                devicesAddresses.add(deviceAddress);
                                serversAdapter.notifyDataSetChanged();
                            }
                        }
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    textScanStatus.setText("[SCAN: COMPLETE]");
                    textScanStatus.setTextColor(getResources().getColor(R.color.cyberpunk_cyan, null));

                    if (serversList.isEmpty()) {
                        Toast.makeText(ListActivity.this, "No se encontraron dispositivos",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };

        // Registrar receptor
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, filter);

        // Iniciar búsqueda
        try {
            bluetoothAdapter.startDiscovery();
            textScanStatus.setText("[SCAN: ACTIVE]");
            Toast.makeText(this, "Buscando servidores...", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al iniciar búsqueda", Toast.LENGTH_SHORT).show();
        }
    }

    private void conectarServidor(int position) {
        String address = devicesAddresses.get(position);

        // Cancelar búsqueda
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        // Ir al juego como CLIENTE
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("role", "CLIENT");
        intent.putExtra("address", address);
        startActivity(intent);
        finish();
    }


    public void goBack(View view) {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cancelar búsqueda
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.cancelDiscovery();
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        // Desregistrar receptor
        try {
            if (receiver != null) {
                unregisterReceiver(receiver);
            }
        } catch (IllegalArgumentException e) {
            // Receptor ya desregistrado
            e.printStackTrace();
        }
    }
}