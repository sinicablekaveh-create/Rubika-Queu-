package ir.sinicable.rubikaqueue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Receives Hooshika results for individual review; it never starts the send service. */
public final class HooshikaInboxActivity extends Activity {
    private final Map<HooshikaHandoff.Item, CheckBox> consentChoices = new LinkedHashMap<>();
    private final Map<HooshikaHandoff.Item, Integer> sourceRows = new LinkedHashMap<>();
    private HooshikaHandoff.Packet packet;
    private EditText message;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Intent.ACTION_SEND.equals(getIntent().getAction())
                || !HooshikaHandoff.MIME.equals(getIntent().getType())) {
            showInvalidPacket();
            return;
        }
        try {
            packet = HooshikaHandoff.parse(getIntent().getStringExtra(Intent.EXTRA_TEXT));
            setContentView(content());
        } catch (Exception ignored) {
            showInvalidPacket();
        }
    }

    private View content() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(30));
        root.setBackgroundColor(Color.rgb(246, 248, 251));
        scroll.addView(root);

        TextView title = text("دریافت نتایج هوشیکا", 25, true, Color.rgb(20, 60, 75));
        root.addView(title, match());
        TextView note = text("نتیجهٔ جست‌وجو به‌تنهایی رضایت پیام نیست. هر مخاطب را جداگانه بررسی کنید. فقط شماره‌های کسب‌وکار پس از تأیید رضایت وارد صف می‌شوند؛ لینک گروه‌ها برای بررسی دستی نمایش داده می‌شود.", 14, false, Color.DKGRAY);
        note.setPadding(0, dp(8), 0, dp(12));
        root.addView(note, match());

        if (packet.duplicatesRemoved > 0) {
            root.addView(text(packet.duplicatesRemoved + " نتیجهٔ تکراری حذف شد.", 13, true, Color.rgb(28, 110, 90)), match());
        }
        int row = 0;
        for (HooshikaHandoff.Item item : packet.items) {
            row++;
            sourceRows.put(item, row);
            root.addView(itemView(item), matchWithTop(8));
        }

        TextView messageLabel = text("متن پیام را بنویسید", 16, true, Color.rgb(20, 60, 75));
        messageLabel.setPadding(0, dp(18), 0, dp(6));
        root.addView(messageLabel, match());
        message = new EditText(this);
        message.setHint("متن پیام");
        message.setGravity(Gravity.TOP | Gravity.RIGHT);
        message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        message.setMinLines(4);
        message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4200)});
        root.addView(message, match());

        Button add = button("افزودن مخاطبانِ تأییدشده به صف ذخیره‌شده", Color.rgb(20, 115, 100));
        add.setOnClickListener(v -> saveConsentedContacts());
        root.addView(add, matchWithTop(14));
        root.addView(text("این مرحله فقط صف را ذخیره می‌کند؛ ارسال شروع نمی‌شود. پیش از شروع صف، متن و گیرندگان را دوباره در برنامه بررسی کنید.", 13, false, Color.GRAY), matchWithTop(8));
        return scroll;
    }

    private View itemView(HooshikaHandoff.Item item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(220, 226, 231));
        card.setBackground(background);

        String type = item.isGroup() ? ("public_channel".equals(item.kind) ? "کانال عمومی؛ بررسی دستی" : "گروه عمومی؛ بررسی دستی") : "مخاطب کسب‌وکار";
        card.addView(text(type, 13, true, Color.rgb(28, 96, 111)), match());
        card.addView(text(item.name.isEmpty() ? "بدون نام" : item.name, 16, true, Color.rgb(35, 45, 55)), matchWithTop(3));
        if (!item.phone.isEmpty()) card.addView(text("+" + item.phone, 14, false, Color.DKGRAY), matchWithTop(3));
        if (!item.url.isEmpty()) card.addView(text(item.url, 12, false, Color.GRAY), matchWithTop(3));
        if (!item.source.isEmpty()) card.addView(text("منبع: " + item.source, 12, false, Color.GRAY), matchWithTop(3));

        if (item.canQueueWithConsent()) {
            CheckBox choice = new CheckBox(this);
            choice.setText("رضایت صریح همین مخاطب برای دریافت این پیام را تأیید می‌کنم؛ افزودن او به صف مجاز است.");
            choice.setTextSize(13);
            choice.setChecked(false);
            consentChoices.put(item, choice);
            card.addView(choice, matchWithTop(5));
        } else if (item.isGroup()) {
            card.addView(text("شناسهٔ حساب برای این لینک دریافت نشده است؛ این نتیجه از اینجا به صف ارسال حساب اضافه نمی‌شود.", 12, false, Color.rgb(120, 75, 20)), matchWithTop(5));
            Button open = button("باز کردن لینک برای بررسی", Color.rgb(65, 95, 160));
            open.setOnClickListener(v -> openGroupLink(item.url));
            card.addView(open, matchWithTop(5));
        } else {
            card.addView(text("شمارهٔ معتبر ندارد؛ فقط برای بازبینی نگه داشته می‌شود.", 12, false, Color.GRAY), matchWithTop(5));
        }
        return card;
    }

    private void saveConsentedContacts() {
        if (QueueService.running) {
            toast("ابتدا صف در حال اجرا را متوقف کنید");
            return;
        }
        List<HooshikaHandoff.Item> selected = new ArrayList<>();
        for (Map.Entry<HooshikaHandoff.Item, CheckBox> entry : consentChoices.entrySet())
            if (entry.getValue().isChecked()) selected.add(entry.getKey());
        if (selected.isEmpty()) {
            toast("برای افزودن به صف، رضایت هر مخاطب را جداگانه تأیید کنید");
            return;
        }
        String body = message.getText().toString().trim();
        if (body.isEmpty()) {
            message.setError("متن پیام را بنویسید");
            return;
        }
        try {
            RubikaClient account = Vault.load(this);
            if (account == null || account.guid.isEmpty()) {
                toast("ابتدا از صفحهٔ اصلی وارد حساب روبیکا شوید");
                return;
            }
            JSONObject old = JobStore.load(this);
            String conflict = existingQueueConflict(old, account.guid);
            if (conflict != null) {
                toast(conflict);
                return;
            }

            ExcelReader.Result rows = new ExcelReader.Result();
            for (HooshikaHandoff.Item item : selected)
                rows.entries.add(new ExcelReader.Entry(item.phone, item.name, sourceRows.get(item)));
            JSONObject job = JobStore.create(rows, body, account.guid);
            job.put("handoff_source", "HooshikaDev");
            job.put("consent_review", "per_item");
            JSONArray recipients = job.getJSONArray("recipients");
            long consentTime = System.currentTimeMillis();
            for (int i = 0; i < recipients.length(); i++) {
                HooshikaHandoff.Item item = selected.get(i);
                JSONObject recipient = recipients.getJSONObject(i);
                recipient.put("consent_confirmed", true);
                recipient.put("consent_confirmed_at", consentTime);
                recipient.put("source_url", item.url);
                recipient.put("source", item.source);
            }
            JobStore.save(this, job);
            toast(selected.size() + " مخاطبِ دارای رضایت در صف ذخیره شد؛ ارسال آغاز نشد");
            Intent main = new Intent(this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(main);
            finish();
        } catch (Exception ignored) {
            toast("ذخیرهٔ صف انجام نشد؛ ورود حساب و وضعیت صف قبلی را بررسی کنید");
        }
    }

    private String existingQueueConflict(JSONObject old, String owner) throws Exception {
        if (old == null) return null;
        if (!owner.equals(old.optString("owner"))) return "صف ذخیره‌شده متعلق به حساب دیگری است؛ آن را از صفحهٔ اصلی بررسی کنید";
        if (JobStore.uncertain(old)) return "ابتدا نتیجهٔ نامشخص صف قبلی را بررسی کنید";
        if (old.optLong("scheduled_start_at", 0) > System.currentTimeMillis()) return "ابتدا زمان‌بندی صف قبلی را لغو یا اجرا کنید";
        JSONArray recipients = old.optJSONArray("recipients");
        if (recipients != null) {
            for (int i = 0; i < recipients.length(); i++) {
                String status = recipients.optJSONObject(i).optString("status");
                if ("pending".equals(status) || "retry_wait".equals(status) || "sending".equals(status))
                    return "برای حفظ صف قبلی، آن را کامل کنید یا گزارشش را ذخیره کنید";
            }
        }
        return null;
    }

    private void openGroupLink(String url) {
        try {
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(open);
        } catch (Exception ignored) {
            toast("مرورگر یا برنامهٔ روبیکا در دسترس نیست");
        }
    }

    private void showInvalidPacket() {
        new AlertDialog.Builder(this)
                .setTitle("فهرست هوشیکا دریافت نشد")
                .setMessage("فهرست ناقص، ناسازگار یا بزرگ‌تر از حد مجاز است. دوباره از داخل هوشیکا نتیجه‌های موردنظر را انتخاب و ارسال کنید.")
                .setPositiveButton("بستن", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        return view;
    }

    private Button button(String value, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        return button;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams matchWithTop(int margin) {
        LinearLayout.LayoutParams params = match();
        params.topMargin = dp(margin);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
