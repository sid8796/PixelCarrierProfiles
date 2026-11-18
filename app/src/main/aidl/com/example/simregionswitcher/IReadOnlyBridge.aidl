package com.example.simregionswitcher;
import android.os.Bundle;
import com.example.simregionswitcher.IReaderCallback;
interface IReadOnlyBridge {
    Bundle readIms(int logicalSlot, int subscriptionId, long requestId, IReaderCallback callback) = 0;
    Bundle readConfig(int logicalSlot, int subscriptionId, long requestId, IReaderCallback callback) = 1;
    Bundle readTransport(int logicalSlot, int subscriptionId) = 2;
    Bundle readCapabilities(int logicalSlot, int subscriptionId) = 3;
    Bundle readImsNumber(int logicalSlot, int subscriptionId) = 4;
    void destroy() = 16777114;
}
