package info.nightscout.androidaps.plugins.DanaR.comm;

import com.squareup.otto.Bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRBolusProgress;
import info.nightscout.utils.DecimalFormatter;

public class MsgBolusProgress extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgBolusProgress.class);
    private static Bus bus = null;

    private static Treatment t;
    private static double amount;

    public int progress = -1;

    public MsgBolusProgress() {
        SetCommand(0x0202);
    }

    public MsgBolusProgress(Bus bus, double amount, Treatment t) {
        this();
        this.amount = amount;
        this.t = t;
        this.bus = bus;
    }

    @Override
    public void handleMessage(byte[] bytes) {
        progress = intFromBuff(bytes, 0, 2);
        Double done = (amount * 100 - progress) / 100d;
        t.insulin = done;
        EventDanaRBolusProgress bolusingEvent = EventDanaRBolusProgress.getInstance();
        bolusingEvent.sStatus = "Delivering " + DecimalFormatter.to1Decimal(done) + "U";
        bolusingEvent.t = t;

        if (Config.logDanaMessageDetail) {
            log.debug("Bolus remaining: " + progress + " delivered: " + done);
        }

        bus.post(bolusingEvent);
    }
}
