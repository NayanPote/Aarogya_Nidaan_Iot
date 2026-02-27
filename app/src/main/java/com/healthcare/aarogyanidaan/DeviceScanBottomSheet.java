package com.healthcare.aarogyanidaan;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class DeviceScanBottomSheet extends BottomSheetDialogFragment {

    public interface OnDeviceSelectedListener {
        void onDeviceSelected(BluetoothDevice device);
    }

    private OnDeviceSelectedListener listener;
    private BluetoothAdapter bluetoothAdapter;
    private DeviceAdapter adapter;
    private final List<BluetoothDevice> deviceList = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView statusText;
    private Button scanButton;

    public void setOnDeviceSelectedListener(OnDeviceSelectedListener l) {
        this.listener = l;
    }

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && !deviceList.contains(device)) {
                    deviceList.add(device);
                    adapter.notifyItemInserted(deviceList.size() - 1);
                    statusText.setText("Found " + deviceList.size() + " device(s)…");
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                progressBar.setVisibility(View.GONE);
                scanButton.setEnabled(true);
                scanButton.setText("Scan Again");
                statusText.setText(deviceList.isEmpty()
                        ? "No devices found. Make sure ESP32 is on."
                        : "Found " + deviceList.size() + " device(s). Tap to connect.");
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Build layout programmatically (no extra XML needed)
        View root = inflater.inflate(R.layout.bottom_sheet_device_scan, container, false);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        progressBar = root.findViewById(R.id.scanProgress);
        statusText  = root.findViewById(R.id.scanStatusText);
        scanButton  = root.findViewById(R.id.scanButton);
        RecyclerView recyclerView = root.findViewById(R.id.deviceRecyclerView);

        adapter = new DeviceAdapter(deviceList, device -> {
            stopScan();
            dismiss();
            if (listener != null) listener.onDeviceSelected(device);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        scanButton.setOnClickListener(v -> startScan());

        // Also show already-paired devices immediately
        loadPairedDevices();
        startScan();

        return root;
    }

    private void loadPairedDevices() {
        if (bluetoothAdapter == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED) {
            return; // permission handled by activity
        }

        for (BluetoothDevice d : bluetoothAdapter.getBondedDevices()) {
            if (!deviceList.contains(d)) {
                deviceList.add(d);
            }
        }

        adapter.notifyDataSetChanged();
        statusText.setText("Paired: " + deviceList.size() + " — scanning for more…");
    }

    private void startScan() {
        deviceList.clear();
        adapter.notifyDataSetChanged();
        loadPairedDevices(); // keep paired devices visible

        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        scanButton.setText("Scanning…");
        statusText.setText("Scanning for devices…");

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        requireContext().registerReceiver(scanReceiver, filter);

        if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
        bluetoothAdapter.startDiscovery();
    }

    private void stopScan() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        try { requireContext().unregisterReceiver(scanReceiver); }
        catch (IllegalArgumentException ignored) {}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopScan();
    }

    // ── Inner RecyclerView Adapter ────────────────────────────────────────────
    static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {

        interface OnClickListener { void onClick(BluetoothDevice device); }

        private final List<BluetoothDevice> list;
        private final OnClickListener clickListener;

        DeviceAdapter(List<BluetoothDevice> list, OnClickListener l) {
            this.list = list;
            this.clickListener = l;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bluetooth_device, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            BluetoothDevice device = list.get(position);
            String name = device.getName();
            holder.nameText.setText(name != null && !name.isEmpty() ? name : "Unknown Device");
            holder.addressText.setText(device.getAddress());
            // Highlight ESP32
            if (BluetoothDataService.DEVICE_NAME.equals(name)) {
                holder.nameText.setTextColor(0xFF4CAF50); // green
                holder.badge.setVisibility(View.VISIBLE);
            } else {
                holder.nameText.setTextColor(0xFF212121);
                holder.badge.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> clickListener.onClick(device));
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView nameText, addressText, badge;
            VH(@NonNull View v) {
                super(v);
                nameText    = v.findViewById(R.id.deviceName);
                addressText = v.findViewById(R.id.deviceAddress);
                badge       = v.findViewById(R.id.espBadge);
            }
        }
    }
}