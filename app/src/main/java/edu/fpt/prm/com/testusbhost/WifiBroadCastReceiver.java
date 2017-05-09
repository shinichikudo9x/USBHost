package edu.fpt.prm.com.testusbhost;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.*;
import android.widget.Toast;

/**
 * Created by HuongLX on 4/27/2017.
 */

public class WifiBroadCastReceiver extends BroadcastReceiver {
    private WifiP2pManager mManager;
    private Channel mChannel;
    private MainActivity mActivity;
    PeerListListener mPeerListListener;
    WifiP2pDevice device;
    WifiP2pConfig config = new WifiP2pConfig();

    public WifiBroadCastReceiver(WifiP2pManager manager, Channel channel,
                                 final MainActivity activity) {
        super();
        this.mManager = manager;
        this.mChannel = channel;
        this.mActivity = activity;

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Check to see if Wi-Fi is enabled and notify appropriate activity
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                mActivity.setIsWifiP2pEnabled(true);
            } else {
                // Wi-Fi P2P is not enabled
                mActivity.setIsWifiP2pEnabled(false);
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // Call WifiP2pManager.requestPeers() to get a list of current peers

            if(mManager!=null){
                mManager.requestPeers(mChannel,mPeerListListener);
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Respond to new connection or disconnections
            mPeerListListener = new PeerListListener() {
                @Override
                public void onPeersAvailable(WifiP2pDeviceList peers) {
                    for(WifiP2pDevice pDevice:peers.getDeviceList()){
                        device = pDevice;
                        config.deviceAddress = device.deviceAddress;
                        mManager.connect(mChannel, config, new ActionListener() {

                            @Override
                            public void onSuccess() {
                                Toast.makeText(mActivity.getBaseContext(),"Connect Success",Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailure(int reason) {
                                //failure logic
                            }
                        });
                    }
                }
            };
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // Respond to this device's wifi state changing
        }
    }

}
