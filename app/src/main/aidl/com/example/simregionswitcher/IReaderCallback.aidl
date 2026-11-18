package com.example.simregionswitcher;
import android.os.Bundle;
oneway interface IReaderCallback {
    void onResult(long requestId, in Bundle result);
}
