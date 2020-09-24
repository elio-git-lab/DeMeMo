package com.erg.memorized.fragments.scorer;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.erg.memorized.R;
import com.erg.memorized.fragments.AdMobFragment;
import com.erg.memorized.helpers.MessagesHelper;
import com.erg.memorized.helpers.RealmHelper;
import com.erg.memorized.helpers.ScoreHelper;
import com.erg.memorized.helpers.SharedPreferencesHelper;
import com.erg.memorized.model.ItemUser;
import com.erg.memorized.model.ItemVerse;
import com.erg.memorized.model.Score;
import com.erg.memorized.util.SuperUtil;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

import static com.erg.memorized.util.Constants.DECIMAL_PLACE;
import static com.erg.memorized.util.Constants.LEADER_BOARD_FIRE_BASE_REFERENCE;
import static com.erg.memorized.util.Constants.SPACE;
import static com.erg.memorized.util.Constants.USER_FIRE_BASE_REFERENCE;

public class ResultFragment extends Fragment {

    public static final String TAG = "ResultFragment";
    private View rootView;
    private ViewGroup container;
    private LinearLayout emptyContainerSignal;
    private ItemVerse verse;
    private ArrayList<Score> scores;
    private ItemUser currentUser;
    private RealmHelper realmHelper;
    private FirebaseAuth fAuth;
    private SharedPreferencesHelper spHelper;
    private Animation animScaleUp, animScaleDown;

    public ResultFragment() {
        // Required empty public constructor
    }


    public ResultFragment(ItemVerse verse, ArrayList<Score> scores) {
        this.verse = verse;
        this.scores = scores;
    }

    public static ResultFragment newInstance(ItemVerse verse,
                                             ArrayList<Score> scores) {
        return new ResultFragment(verse, scores);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        spHelper = new SharedPreferencesHelper(requireContext());
        fAuth = FirebaseAuth.getInstance();
        realmHelper = new RealmHelper(requireContext());

        if (spHelper.getUserLoginStatus()) {
            currentUser = realmHelper.getUser();
        }

        animScaleUp = AnimationUtils.loadAnimation(getContext(), R.anim.scale_up);
        animScaleDown = AnimationUtils.loadAnimation(getContext(), R.anim.scale_down);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_result, container, false);
        this.container = container;
        setUpView();
        return rootView;
    }

    private void setUpView() {

        LinearLayout scorerContainer = rootView.findViewById(R.id.ll_scorer_container);
        ScrollView scrollViewScorerContainer = rootView
                .findViewById(R.id.scroll_view_scorers_container);
        emptyContainerSignal = rootView.findViewById(R.id.linear_layout_empty_container);

        if (scores.isEmpty() && emptyContainerSignal.getVisibility() == View.GONE) {
            SuperUtil.showView(animScaleUp, emptyContainerSignal);
            SuperUtil.hideView(animScaleDown, scrollViewScorerContainer);
        }


        TableLayout fillMissingEvaluatorTable = (TableLayout) getLayoutInflater()
                .inflate(R.layout.table_score_view, container, false);

        TableLayout dragAndDropEvaluatorTable = (TableLayout) getLayoutInflater()
                .inflate(R.layout.table_score_view, container, false);

        TableLayout writerEvaluatorTable = (TableLayout) getLayoutInflater()
                .inflate(R.layout.table_score_view, container, false);


        String[] evaluatorNames = getResources().getStringArray(R.array.evaluator_names);

        TableLayout[] tableLayouts = new TableLayout[]{
                fillMissingEvaluatorTable,
                dragAndDropEvaluatorTable,
                writerEvaluatorTable
        };


        TextView tvTitle = rootView.findViewById(R.id.tv_title);
        TextView tvTotal = rootView.findViewById(R.id.tv_total_user_score);
        tvTitle.setText(verse.getTitle());

        for (int i = 0; i < scores.size(); i++) {
            TableLayout table = tableLayouts[i];
            Score score = scores.get(i);
            String evaluatorName = evaluatorNames[i];

            TextView tvEvaluatorName = table.findViewById(R.id.tv_evaluator_name);
            TextView tvHitsSum = table.findViewById(R.id.tv_hits_sum);
            TextView tvAlmostSum = table.findViewById(R.id.tv_almost_sum);
            TextView tvMissSum = table.findViewById(R.id.tv_miss_sum);
            TextView tvTotalScore = table.findViewById(R.id.tv_total_score_row);

            tvEvaluatorName.setText(evaluatorName);
            tvHitsSum.setText(String.valueOf(score.getHitsCont()));
            tvAlmostSum.setText(String.valueOf(score.getAlmostHitsCont()));
            tvMissSum.setText(String.valueOf(score.getMissCont()));

            float evaluatorTotalScore = ScoreHelper.getEvaluatorScore(score);
            String stringBuilder = getString(R.string.score_total_partial)
                    + SPACE + ScoreHelper.round(evaluatorTotalScore, DECIMAL_PLACE);
            tvTotalScore.setText(stringBuilder);

            scorerContainer.addView(table);

        }
        float totalScore = ScoreHelper.getTotalScore(scores);
        String stringBuilder = getString(R.string.score_total) + SPACE +
                ScoreHelper.round(totalScore, DECIMAL_PLACE);
        tvTotal.setText(stringBuilder);

        TableRow tableRowAlmost = tableLayouts[2].findViewById(R.id.table_row_almost);
        SuperUtil.showView(null, tableRowAlmost);

        verse.setVerseScore(totalScore);
        realmHelper.addVerseToDB(verse);
        currentUser.setScore(ScoreHelper.getUserScoreByVersesList(realmHelper.getSavedVerses()));
        realmHelper.addUserToDB(currentUser);

        uploadScore();
    }

    private void uploadScore() {
        if (spHelper.getUserLoginStatus()) {
            Dialog pgsDialog = SuperUtil.showProgressDialog(requireActivity(), container);
            DatabaseReference fReference = FirebaseDatabase.getInstance()
                    .getReference(LEADER_BOARD_FIRE_BASE_REFERENCE)
                    .child(currentUser.getId());
            fReference.setValue(currentUser.getScoreInfoIntoHasMap())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            if (pgsDialog.isShowing())
                                pgsDialog.dismiss();

                            if (isVisible())
                                MessagesHelper.showInfoMessage(requireActivity(),
                                        getString(R.string.upload_success));
                            if (!currentUser.isPremium())
                                handleAdd();
                        } else {
                            Log.e(TAG, "uploadScore: " + task.getException().getMessage());
                            if (task.getException() instanceof FirebaseNetworkException) {
                                if (isVisible())
                                    MessagesHelper.showInfoMessageWarning(requireActivity(),
                                            getString(R.string.network_error));
                            } else {
                                if (isVisible())
                                    MessagesHelper.showInfoMessageError(requireActivity(),
                                            getString(R.string.failed_uploading));
                            }
                        }
                    });

            updateUserOnFireBase(pgsDialog);
        } else {
            if (isVisible())
                MessagesHelper.showInfoMessageWarning(requireActivity(),
                        getString(R.string.login_needed));
        }
    }

    private void handleAdd() {
        new Handler().postDelayed(() -> {
            if (isVisible()){
                SuperUtil.loadView(
                        requireActivity(),
                        AdMobFragment.newInstance(currentUser),
                        AdMobFragment.TAG, true);
            }
        }, 2000);
    }

    private void updateUserOnFireBase(Dialog pgsDialog) {
        DatabaseReference fReferenceUser = FirebaseDatabase.getInstance()
                .getReference(USER_FIRE_BASE_REFERENCE)
                .child(currentUser.getId());

        fReferenceUser.setValue(currentUser.getUserIntoHasMap())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User Score uploadScore: Success ");
                    } else {
                        Log.e(TAG, "User uploadScore: " + task.getException().getMessage());
                    }
                    if (pgsDialog.isShowing())
                        pgsDialog.dismiss();
                });
    }

    private void handleToShowRateDialog() {
        new Handler().postDelayed(() -> {
            if (isVisible())
                MessagesHelper.showRateDialog(requireActivity(), animScaleUp);
        }, 2000);
    }

}