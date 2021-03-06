package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.PlusMinusEditText;
import info.nightscout.utils.Round;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class WizardDialog extends DialogFragment implements OnClickListener {

    Button wizardDialogDeliverButton;
    TextView correctionInput;
    TextView carbsInput;
    TextView bgInput;
    TextView bg, bgInsulin, bgUnits;
    CheckBox bgCheckbox;
    TextView carbs, carbsInsulin;
    TextView bolusIobInsulin;
    TextView basalIobInsulin;
    CheckBox bolusIobCheckbox;
    CheckBox basalIobCheckbox;
    TextView correctionInsulin;
    TextView total, totalInsulin;
    EditText carbTimeEdit;
    Spinner profileSpinner;

    PlusMinusEditText editBg;
    PlusMinusEditText editCarbs;
    PlusMinusEditText editCorr;
    PlusMinusEditText editCarbTime;

    Integer calculatedCarbs = 0;
    Double calculatedTotalInsulin = 0d;
    JSONObject boluscalcJSON;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    public WizardDialog() {
        mHandlerThread = new HandlerThread(NewExtendedBolusDialog.class.getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            calculateInsulin();
        }
    };

    final CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            calculateInsulin();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_wizard_dialog, null, false);

        wizardDialogDeliverButton = (Button) view.findViewById(R.id.treatments_wizard_deliverButton);
        wizardDialogDeliverButton.setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        correctionInput = (TextView) view.findViewById(R.id.treatments_wizard_correctioninput);
        carbsInput = (TextView) view.findViewById(R.id.treatments_wizard_carbsinput);
        bgInput = (TextView) view.findViewById(R.id.treatments_wizard_bginput);

        correctionInput.addTextChangedListener(textWatcher);
        carbsInput.addTextChangedListener(textWatcher);
        bgInput.addTextChangedListener(textWatcher);

        bg = (TextView) view.findViewById(R.id.treatments_wizard_bg);
        bgInsulin = (TextView) view.findViewById(R.id.treatments_wizard_bginsulin);
        bgUnits = (TextView) view.findViewById(R.id.treatments_wizard_bgunits);
        bgCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_bgcheckbox);
        carbs = (TextView) view.findViewById(R.id.treatments_wizard_carbs);
        carbsInsulin = (TextView) view.findViewById(R.id.treatments_wizard_carbsinsulin);
        bolusIobInsulin = (TextView) view.findViewById(R.id.treatments_wizard_bolusiobinsulin);
        basalIobInsulin = (TextView) view.findViewById(R.id.treatments_wizard_basaliobinsulin);
        bolusIobCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_bolusiobcheckbox);
        basalIobCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_basaliobcheckbox);
        correctionInsulin = (TextView) view.findViewById(R.id.treatments_wizard_correctioninsulin);
        total = (TextView) view.findViewById(R.id.treatments_wizard_total);
        totalInsulin = (TextView) view.findViewById(R.id.treatments_wizard_totalinsulin);
        carbTimeEdit = (EditText) view.findViewById(R.id.treatments_wizard_carbtimeinput);
        profileSpinner = (Spinner) view.findViewById(R.id.treatments_wizard_profile);

        bgCheckbox.setOnCheckedChangeListener(onCheckedChangeListener);
        basalIobCheckbox.setOnCheckedChangeListener(onCheckedChangeListener);
        bolusIobCheckbox.setOnCheckedChangeListener(onCheckedChangeListener);

        Integer maxCarbs = MainApp.getConfigBuilder().applyCarbsConstraints(Constants.carbsOnlyForCheckLimit);
        Double maxCorrection = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);

        editBg = new PlusMinusEditText(view, R.id.treatments_wizard_bginput, R.id.treatments_wizard_bginput_plus, R.id.treatments_wizard_bginput_minus, 0d, 0d, 500d, 0.1d, new DecimalFormat("0.0"), false);
        editCarbs = new PlusMinusEditText(view, R.id.treatments_wizard_carbsinput, R.id.treatments_wizard_carbsinput_plus, R.id.treatments_wizard_carbsinput_minus, 0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false);
        editCorr = new PlusMinusEditText(view, R.id.treatments_wizard_correctioninput, R.id.treatments_wizard_correctioninput_plus, R.id.treatments_wizard_correctioninput_minus, 0d, 0d, maxCorrection, 0.05d, new DecimalFormat("0.00"), false);
        editCarbTime = new PlusMinusEditText(view, R.id.treatments_wizard_carbtimeinput, R.id.treatments_wizard_carbtime_plus, R.id.treatments_wizard_carbtime_minus, 0d, -60d, 60d, 5d, new DecimalFormat("0"), false);
        initDialog();

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.treatments_wizard_deliverButton:
                if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d) {
                    DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
                    String confirmMessage = getString(R.string.entertreatmentquestion);

                    Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(calculatedTotalInsulin);
                    Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(calculatedCarbs);

                    confirmMessage += "\n" + getString(R.string.bolus) + ": " + formatNumber2decimalplaces.format(insulinAfterConstraints) + "U";
                    confirmMessage += "\n" + getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";

                    if (insulinAfterConstraints - calculatedTotalInsulin != 0 || carbsAfterConstraints != calculatedCarbs) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle(getContext().getString(R.string.treatmentdeliveryerror));
                        builder.setMessage(getString(R.string.constraints_violation) + "\n" + getString(R.string.changeyourinput));
                        builder.setPositiveButton(getContext().getString(R.string.ok), null);
                        builder.show();
                        return;
                    }

                    final Double finalInsulinAfterConstraints = insulinAfterConstraints;
                    final Integer finalCarbsAfterConstraints = carbsAfterConstraints;

                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(getContext().getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                                final ConfigBuilderFragment pump = MainApp.getConfigBuilder();
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        PumpEnactResult result = pump.deliverTreatmentFromBolusWizard(
                                                finalInsulinAfterConstraints,
                                                finalCarbsAfterConstraints,
                                                SafeParse.stringToDouble(bgInput.getText().toString()),
                                                "Manual",
                                                SafeParse.stringToInt(carbTimeEdit.getText().toString()),
                                                boluscalcJSON
                                        );
                                        if (!result.success) {
                                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                            builder.setTitle(getContext().getString(R.string.treatmentdeliveryerror));
                                            builder.setMessage(result.comment);
                                            builder.setPositiveButton(getContext().getString(R.string.ok), null);
                                            builder.show();
                                        }
                                    }
                                });
                            }
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();
                }
                break;
        }

    }

    private void initDialog() {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();

        if (profile == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.noprofile));
            return;
        }

        ArrayList<CharSequence> profileList;
        profileList = profile.getProfileList();
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(),
                android.R.layout.simple_spinner_item, profileList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(adapter);
        // set selected to actual profile
        for (int p = 0; p < profileList.size(); p++) {
            if (profileList.get(p).equals(profile.getActiveProfile()))
                profileSpinner.setSelection(p);
        }

        String units = profile.getUnits();
        bgUnits.setText(units);
        if (units.equals(Constants.MGDL)) editBg.setStep(1d);
        else editBg.setStep(0.1d);

        // Set BG if not old
        BgReading lastBg = MainApp.getDbHelper().actualBg();

        if (lastBg != null) {
            Double lastBgValue = lastBg.valueToUnits(units);
            Double sens = profile.getIsf(NSProfile.secondsFromMidnight());
            Double targetBGLow = profile.getTargetLow(NSProfile.secondsFromMidnight());
            Double targetBGHigh = profile.getTargetHigh(NSProfile.secondsFromMidnight());
            Double bgDiff;
            if (lastBgValue <= targetBGLow) {
                bgDiff = lastBgValue - targetBGLow;
            } else {
                bgDiff = lastBgValue - targetBGHigh;
            }

            bg.setText(lastBg.valueToUnitsToString(units) + " ISF: " + DecimalFormatter.to0Decimal(sens));
            bgInsulin.setText(DecimalFormatter.to2Decimal(bgDiff / sens) + "U");
            bgInput.removeTextChangedListener(textWatcher);
            //bgInput.setText(lastBg.valueToUnitsToString(units));
            editBg.setValue(lastBg.valueToUnits(units));
            bgInput.addTextChangedListener(textWatcher);
        } else {
            bg.setText("");
            bgInsulin.setText("");
            bgInput.removeTextChangedListener(textWatcher);
            //bgInput.setText("");
            editBg.setValue(0d);
            bgInput.addTextChangedListener(textWatcher);
        }

        // IOB calculation
        TreatmentsInterface treatments = MainApp.getConfigBuilder().getActiveTreatments();
        TempBasalsInterface tempBasals = MainApp.getConfigBuilder().getActiveTempBasals();
        treatments.updateTotalIOB();
        tempBasals.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        IobTotal basalIob = tempBasals.getLastCalculation();

        bolusIobInsulin.setText(DecimalFormatter.to2Decimal(-bolusIob.iob) + "U");
        basalIobInsulin.setText(DecimalFormatter.to2Decimal(-basalIob.basaliob) + "U");

        totalInsulin.setText("");
        wizardDialogDeliverButton.setVisibility(Button.INVISIBLE);

   }

    private void calculateInsulin() {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profileSpinner == null || profileSpinner.getSelectedItem() == null)
            return; // not initialized yet
        String selectedAlternativeProfile = profileSpinner.getSelectedItem().toString();
        JSONObject specificProfile = profile.getSpecificProfile(selectedAlternativeProfile);

        // Entered values
        Double c_bg = SafeParse.stringToDouble(bgInput.getText().toString());
        Integer c_carbs = SafeParse.stringToInt(carbsInput.getText().toString());
        Double c_correction = SafeParse.stringToDouble(correctionInput.getText().toString());
        Double corrAfterConstraint = MainApp.getConfigBuilder().applyBolusConstraints(c_correction);
        if (c_correction - corrAfterConstraint != 0) { // c_correction != corrAfterConstraint doesn't work
            correctionInput.removeTextChangedListener(textWatcher);
            correctionInput.setText("");
            correctionInput.addTextChangedListener(textWatcher);
            //wizardDialogDeliverButton.setVisibility(Button.GONE);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.bolusconstraintapplied));
            return;
        }
        Integer carbsAfterConstraint = MainApp.getConfigBuilder().applyCarbsConstraints(c_carbs);
        if (c_carbs != carbsAfterConstraint) {
            carbsInput.removeTextChangedListener(textWatcher);
            carbsInput.setText("");
            carbsInput.addTextChangedListener(textWatcher);
            //wizardDialogDeliverButton.setVisibility(Button.GONE);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.carbsconstraintapplied));
            return;
        }


        // Insulin from BG
        Double sens = profile.getIsf(specificProfile, NSProfile.secondsFromMidnight());
        Double targetBGLow = profile.getTargetLow(specificProfile, NSProfile.secondsFromMidnight());
        Double targetBGHigh = profile.getTargetHigh(specificProfile, NSProfile.secondsFromMidnight());
        Double bgDiff;
        if (c_bg <= targetBGLow) {
            bgDiff = c_bg - targetBGLow;
        } else {
            bgDiff = c_bg - targetBGHigh;
        }
        Double insulinFromBG = (bgCheckbox.isChecked() && c_bg != 0d) ? bgDiff / sens : 0d;
        bg.setText(c_bg + " ISF: " + DecimalFormatter.to0Decimal(sens));
        bgInsulin.setText(DecimalFormatter.to2Decimal(insulinFromBG) + "U");

        // Insuling from carbs
        Double ic = profile.getIc(specificProfile, NSProfile.secondsFromMidnight());
        Double insulinFromCarbs = c_carbs / ic;
        carbs.setText(DecimalFormatter.to0Decimal(c_carbs) + "g IC: " + DecimalFormatter.to0Decimal(ic));
        carbsInsulin.setText(DecimalFormatter.to2Decimal(insulinFromCarbs) + "U");

        // Insulin from IOB
        TreatmentsInterface treatments = MainApp.getConfigBuilder().getActiveTreatments();
        TempBasalsInterface tempBasals = MainApp.getConfigBuilder().getActiveTempBasals();
        treatments.updateTotalIOB();
        tempBasals.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        IobTotal basalIob = tempBasals.getLastCalculation();

        Double insulingFromBolusIOB = bolusIobCheckbox.isChecked() ? -bolusIob.iob : 0d;
        Double insulingFromBasalsIOB = basalIobCheckbox.isChecked() ? -basalIob.basaliob : 0d;
        bolusIobInsulin.setText(DecimalFormatter.to2Decimal(insulingFromBolusIOB) + "U");
        basalIobInsulin.setText(DecimalFormatter.to2Decimal(insulingFromBasalsIOB) + "U");

        // Insulin from correction
        Double insulinFromCorrection = corrAfterConstraint;
        correctionInsulin.setText(DecimalFormatter.to2Decimal(insulinFromCorrection) + "U");

        // Total
        calculatedTotalInsulin = insulinFromBG + insulinFromCarbs + insulingFromBolusIOB + insulingFromBasalsIOB + insulinFromCorrection;

        if (calculatedTotalInsulin < 0) {
            Double carbsEquivalent = -calculatedTotalInsulin * ic;
            total.setText(getString(R.string.missing) + " " + DecimalFormatter.to0Decimal(carbsEquivalent) + "g");
            calculatedTotalInsulin = 0d;
            totalInsulin.setText("");
        } else {
            calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, 0.05d);
            total.setText("");
            totalInsulin.setText(DecimalFormatter.to2Decimal(calculatedTotalInsulin) + "U");
        }

        calculatedCarbs = c_carbs;

        if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d) {
            String insulinText = calculatedTotalInsulin > 0d ? (DecimalFormatter.to2Decimal(calculatedTotalInsulin) + "U") : "";
            String carbsText = calculatedCarbs > 0d ? (DecimalFormatter.to0Decimal(calculatedCarbs) + "g") : "";
            wizardDialogDeliverButton.setText(getString(R.string.send) + " " + insulinText + " " + carbsText);
            wizardDialogDeliverButton.setVisibility(Button.VISIBLE);
        } else {
            wizardDialogDeliverButton.setVisibility(Button.INVISIBLE);
        }

        boluscalcJSON = new JSONObject();
        try {
            boluscalcJSON.put("profile", selectedAlternativeProfile);
            boluscalcJSON.put("eventTime", DateUtil.toISOString(new Date()));
            boluscalcJSON.put("targetBGLow", targetBGLow);
            boluscalcJSON.put("targetBGHigh", targetBGHigh);
            boluscalcJSON.put("isf", sens);
            boluscalcJSON.put("ic", ic);
            boluscalcJSON.put("iob", -(insulingFromBolusIOB + insulingFromBasalsIOB));
            boluscalcJSON.put("bolusiobused", bolusIobCheckbox.isChecked());
            boluscalcJSON.put("basaliobused", basalIobCheckbox.isChecked());
            boluscalcJSON.put("bg", c_bg);
            boluscalcJSON.put("insulinbg", insulinFromBG);
            boluscalcJSON.put("insulinbgused", bgCheckbox.isChecked());
            boluscalcJSON.put("bgdiff", bgDiff);
            boluscalcJSON.put("insulincarbs", insulinFromCarbs);
            boluscalcJSON.put("carbs", c_carbs);
            boluscalcJSON.put("othercorrection", corrAfterConstraint);
            boluscalcJSON.put("insulin", calculatedTotalInsulin);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}