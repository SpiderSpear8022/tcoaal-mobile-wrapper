package com.example.rpgmakermobileshell;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int PICK_GAME_FOLDER = 1;
    private static final int PICK_ASSETS_FOLDER = 2;
    private static final String PREFERENCES = "game_folder";
    private static final String TREE_URI = "tree_uri";
    private static final String ASSETS_TREE_URI = "assets_tree_uri";
    private static final String DECRYPTED_DIRECTORY = "decrypted";
    private static final String DECRYPTION_MANIFEST = ".tcoaal-decryption-manifest-v1.txt";
    private static final String DECRYPTION_COMPLETE_MARKER = "COMPLETE";
    private static final String LANGUAGE_DATA_PATH = "data/9c7050ae76645487";
    private static final String MOBILE_PLUGIN_PATH = "js/plugins/MobileTextFix.js";
    private static final String MOBILE_LANGUAGE_PATH = "mobile-language.json";
    private static final String GAME_ORIGIN = "https://game.local";
    private static final String TAG = "RpgMakerMobileShell";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final long ASSET_CACHE_LIMIT_BYTES = 512L * 1024 * 1024;
    private static final long MAX_CACHED_FILE_BYTES = 16L * 1024 * 1024;

    private WebView webView;
    private volatile DocumentFile gameRoot;
    private volatile DocumentFile decryptedAssetsRoot;
    private volatile String languagePackJson;
    private volatile int currentControlMask;
    private final ConcurrentMap<String, Map<String, DocumentFile>> directoryChildren =
            new ConcurrentHashMap<>();
    private final Object assetCacheLock = new Object();
    private final ExecutorService assetDecryptExecutor = Executors.newSingleThreadExecutor();
    private FrameLayout gameContainer;
    private VirtualControlsView controlsView;
    private VirtualControlsView.Layout controlLayout;
    private LinearLayout launcher;
    private ScrollView launcherScroll;
    private TextView gameFolderLabel;
    private TextView assetsFolderLabel;
    private ProgressDialog assetDecryptionDialog;
    private volatile boolean assetDecryptionInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildLauncher();
        showLauncher();
    }

    private void buildLauncher() {
        int padding = dp(24);
        launcher = new LinearLayout(this);
        launcher.setOrientation(LinearLayout.VERTICAL);
        launcher.setGravity(Gravity.CENTER);
        launcher.setPadding(padding, padding, padding, padding);
        launcher.setBackgroundColor(Color.rgb(20, 20, 24));

        TextView title = new TextView(this);
        title.setText("RPG Maker Mobile Shell");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        launcher.addView(title, matchWrap());

        TextView instructions = new TextView(this);
        instructions.setText(
                "The game is not included in this app. Select your original www folder. "
                        + "If it has no usable decrypted folder, the app creates www/decrypted "
                        + "from its audio, data, and image assets. Originals stay in place.");
        instructions.setTextColor(Color.LTGRAY);
        instructions.setTextSize(16);
        instructions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = matchWrap();
        textParams.topMargin = dp(16);
        textParams.bottomMargin = dp(12);
        launcher.addView(instructions, textParams);

        gameFolderLabel = new TextView(this);
        gameFolderLabel.setTextColor(Color.LTGRAY);
        gameFolderLabel.setTextSize(14);
        gameFolderLabel.setGravity(Gravity.CENTER);
        launcher.addView(gameFolderLabel, matchWrap());

        assetsFolderLabel = new TextView(this);
        assetsFolderLabel.setTextColor(Color.LTGRAY);
        assetsFolderLabel.setTextSize(14);
        assetsFolderLabel.setGravity(Gravity.CENTER);
        assetsFolderLabel.setText("Decrypted assets are checked or created inside www.");
        LinearLayout.LayoutParams assetsLabelParams = matchWrap();
        assetsLabelParams.topMargin = dp(8);
        launcher.addView(assetsFolderLabel, assetsLabelParams);

        Button chooseGameButton = new Button(this);
        chooseGameButton.setText("Select original www folder");
        chooseGameButton.setOnClickListener(view -> chooseFolder(PICK_GAME_FOLDER));
        LinearLayout.LayoutParams chooseGameParams = wrapWrap();
        chooseGameParams.topMargin = dp(12);
        launcher.addView(chooseGameButton, chooseGameParams);

        Button chooseAssetsButton = new Button(this);
        chooseAssetsButton.setText("Select decrypted assets folder");
        chooseAssetsButton.setOnClickListener(view -> chooseFolder(PICK_ASSETS_FOLDER));
        LinearLayout.LayoutParams chooseAssetsParams = wrapWrap();
        chooseAssetsParams.topMargin = dp(6);
        launcher.addView(chooseAssetsButton, chooseAssetsParams);

        Button launchButton = new Button(this);
        launchButton.setText("Launch game");
        launchButton.setOnClickListener(view -> launchSelectedGame());

        Button customizeButton = new Button(this);
        customizeButton.setText("Customize controls");
        customizeButton.setOnClickListener(view -> showControlLayoutEditor());
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams launchParams = wrapWrap();
        launchParams.topMargin = dp(12);
        launchParams.rightMargin = dp(4);
        actions.addView(launchButton, launchParams);
        LinearLayout.LayoutParams customizeParams = wrapWrap();
        customizeParams.topMargin = dp(12);
        customizeParams.leftMargin = dp(4);
        actions.addView(customizeButton, customizeParams);
        launcher.addView(actions, matchWrap());

        launcherScroll = new ScrollView(this);
        launcherScroll.setFillViewport(true);
        launcherScroll.addView(launcher, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void showLauncher() {
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        gameRoot = null;
        getWindow().getDecorView().setSystemUiVisibility(0);

        DocumentFile savedGameRoot = getSavedFolder(TREE_URI);
        DocumentFile savedAssetsRoot = getSavedFolder(ASSETS_TREE_URI);
        DocumentFile internalAssetsRoot = findChild(savedGameRoot, DECRYPTED_DIRECTORY);
        DocumentFile assetsRoot = isSupportedAssetsFolder(savedAssetsRoot)
                ? savedAssetsRoot
                : internalAssetsRoot;
        gameFolderLabel.setText(isSupportedGameFolder(savedGameRoot)
                ? "Original www folder: selected."
                : "Original www folder: not selected.");
        assetsFolderLabel.setText(isSupportedAssetsFolder(assetsRoot)
                ? "Decrypted assets folder: ready."
                : "Decrypted assets folder: not ready.");
        setContentView(launcherScroll);
    }

    private void showControlLayoutEditor() {
        controlLayout = loadControlLayout();
        FrameLayout editor = new FrameLayout(this);
        editor.setBackgroundColor(Color.rgb(12, 14, 20));
        controlsView = new VirtualControlsView(this, new VirtualControlsView.Listener() {
            @Override
            public void onControlsChanged(int controlMask) {
            }

            @Override
            public void onLayoutChanged(VirtualControlsView.Layout layout) {
                saveControlLayout(layout);
            }
        }, controlLayout, true);
        editor.addView(controlsView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(Color.argb(190, 18, 22, 32));
        toolbar.addView(editorLabel("Drag controls to move them"));
        addEditorButton(toolbar, "Size -", () -> {
            controlsView.setScale(controlLayout.scale - 0.1f);
            saveControlLayout(controlLayout);
        });
        addEditorButton(toolbar, "Size +", () -> {
            controlsView.setScale(controlLayout.scale + 0.1f);
            saveControlLayout(controlLayout);
        });
        addEditorButton(toolbar, "Opacity -", () -> {
            controlsView.setOpacity(controlLayout.opacity - 0.1f);
            saveControlLayout(controlLayout);
        });
        addEditorButton(toolbar, "Opacity +", () -> {
            controlsView.setOpacity(controlLayout.opacity + 0.1f);
            saveControlLayout(controlLayout);
        });
        addEditorButton(toolbar, "Reset", () -> controlsView.resetLayout());
        addEditorButton(toolbar, "Done", this::showLauncher);

        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        editor.addView(toolbar, toolbarParams);
        setContentView(editor);
    }

    private TextView editorLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(8), dp(4), dp(8), dp(4));
        return label;
    }

    private void addEditorButton(LinearLayout toolbar, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(11);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setOnClickListener(view -> action.run());
        toolbar.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)));
    }

    private VirtualControlsView.Layout loadControlLayout() {
        VirtualControlsView.Layout layout = new VirtualControlsView.Layout();
        android.content.SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        layout.dpadX = preferences.getFloat("controls_dpad_x",
                preferences.getFloat("controls_joystick_x", layout.dpadX));
        layout.dpadY = preferences.getFloat("controls_dpad_y",
                preferences.getFloat("controls_joystick_y", layout.dpadY));
        layout.scale = preferences.getFloat("controls_scale", layout.scale);
        layout.opacity = preferences.getFloat("controls_opacity", layout.opacity);
        for (int i = 0; i < layout.buttonX.length; i++) {
            layout.buttonX[i] = preferences.getFloat("controls_button_x_" + i, layout.buttonX[i]);
            layout.buttonY[i] = preferences.getFloat("controls_button_y_" + i, layout.buttonY[i]);
        }
        return layout;
    }

    private void saveControlLayout(VirtualControlsView.Layout layout) {
        android.content.SharedPreferences.Editor editor =
                getPreferences(MODE_PRIVATE).edit()
                        .putFloat("controls_dpad_x", layout.dpadX)
                        .putFloat("controls_dpad_y", layout.dpadY)
                        .putFloat("controls_scale", layout.scale)
                        .putFloat("controls_opacity", layout.opacity);
        for (int i = 0; i < layout.buttonX.length; i++) {
            editor.putFloat("controls_button_x_" + i, layout.buttonX[i]);
            editor.putFloat("controls_button_y_" + i, layout.buttonY[i]);
        }
        editor.apply();
    }

    private void chooseFolder(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (requestCode == PICK_GAME_FOLDER) {
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        if ((requestCode != PICK_GAME_FOLDER && requestCode != PICK_ASSETS_FOLDER)
                || resultCode != RESULT_OK || result == null) {
            return;
        }

        Uri treeUri = result.getData();
        if (treeUri == null) {
            showMessage("The folder picker did not return a folder.");
            return;
        }

        DocumentFile selectedRoot = DocumentFile.fromTreeUri(this, treeUri);
        ArrayList<String> missingFiles = requestCode == PICK_GAME_FOLDER
                ? getMissingGameFiles(selectedRoot)
                : getMissingAssetsFiles(selectedRoot);
        if (!missingFiles.isEmpty()) {
            showUnsupportedFolder(requestCode, missingFiles);
            return;
        }

        int permissionFlags = result.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(treeUri, permissionFlags);
        } catch (SecurityException exception) {
            showMessage("Android did not grant persistent access to that folder. Please select it again.");
            return;
        }

        String preferenceKey = requestCode == PICK_GAME_FOLDER ? TREE_URI : ASSETS_TREE_URI;
        android.content.SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        android.content.SharedPreferences.Editor preferenceEditor = preferences.edit()
                .putString(preferenceKey, treeUri.toString());
        if (requestCode == PICK_GAME_FOLDER
                && !treeUri.toString().equals(preferences.getString(TREE_URI, null))) {
            preferenceEditor.remove(ASSETS_TREE_URI);
        }
        preferenceEditor.apply();
        if (requestCode == PICK_GAME_FOLDER) {
            directoryChildren.clear();
            gameRoot = selectedRoot;
            decryptedAssetsRoot = null;
            showLauncher();
            DocumentFile internalAssetsRoot = findChild(selectedRoot, DECRYPTED_DIRECTORY);
            if (isSupportedAssetsFolder(internalAssetsRoot)) {
                decryptedAssetsRoot = internalAssetsRoot;
                showMessage("Decrypted assets found inside www.");
            } else {
                startAssetDecryption(selectedRoot);
            }
        } else {
            decryptedAssetsRoot = selectedRoot;
            showLauncher();
            showMessage("Decrypted assets selected.");
        }
    }

    private void launchSelectedGame() {
        if (assetDecryptionInProgress) {
            showMessage("Please wait for the asset preparation to finish.");
            return;
        }
        DocumentFile selectedGameRoot = getSavedFolder(TREE_URI);
        DocumentFile internalAssetsRoot = findChild(selectedGameRoot, DECRYPTED_DIRECTORY);
        DocumentFile selectedAssetsRoot = getSavedFolder(ASSETS_TREE_URI);
        if (!isSupportedAssetsFolder(selectedAssetsRoot)) {
            selectedAssetsRoot = internalAssetsRoot;
        }
        ArrayList<String> missingFiles = getMissingGameFiles(selectedGameRoot);
        if (missingFiles.isEmpty() && !isSupportedAssetsFolder(selectedAssetsRoot)) {
            startAssetDecryption(selectedGameRoot);
            return;
        }
        for (String missing : getMissingAssetsFiles(selectedAssetsRoot)) {
            missingFiles.add("Decrypted assets: " + missing);
        }
        if (!missingFiles.isEmpty()) {
            showUnsupportedFolder(PICK_GAME_FOLDER, missingFiles);
            return;
        }
        try {
            languagePackJson = readLanguagePack(selectedAssetsRoot);
        } catch (IOException | SecurityException exception) {
            Log.e(TAG, "Unable to load the English text data from the decrypted assets.", exception);
            showMessage("The decrypted assets' English text data is missing or unreadable. "
                    + "Select the complete decrypted assets folder.");
            return;
        }
        launchGame(selectedGameRoot, selectedAssetsRoot);
    }

    private void startAssetDecryption(DocumentFile gameFolder) {
        if (assetDecryptionInProgress) {
            return;
        }
        assetDecryptionInProgress = true;
        assetDecryptionDialog = new ProgressDialog(this);
        assetDecryptionDialog.setTitle("Preparing game assets");
        assetDecryptionDialog.setMessage("Scanning the selected www folder...");
        assetDecryptionDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        assetDecryptionDialog.setMax(1000);
        assetDecryptionDialog.setProgress(0);
        assetDecryptionDialog.setCancelable(false);
        assetDecryptionDialog.show();

        assetDecryptExecutor.execute(() -> {
            try {
                DecryptionSummary summary = decryptGameAssets(gameFolder, progress -> {
                    runOnUiThread(() -> {
                        if (assetDecryptionDialog != null) {
                            assetDecryptionDialog.setMessage(progress.message);
                            assetDecryptionDialog.setProgress(progress.value);
                        }
                    });
                });
                directoryChildren.clear();
                DocumentFile outputRoot = findDirectChild(gameFolder, DECRYPTED_DIRECTORY);
                ArrayList<String> missingFiles = getMissingAssetsFiles(outputRoot);
                if (!missingFiles.isEmpty()) {
                    throw new IOException("Decrypted output is incomplete: " + missingFiles);
                }
                decryptedAssetsRoot = outputRoot;
                runOnUiThread(() -> {
                    assetDecryptionInProgress = false;
                    dismissAssetDecryptionDialog();
                    showLauncher();
                    showMessage("Asset preparation finished: " + summary.decrypted
                            + " decrypted, " + summary.copied + " copied unchanged.");
                });
            } catch (IOException | SecurityException exception) {
                Log.e(TAG, "Unable to prepare decrypted assets in the selected www folder.",
                        exception);
                runOnUiThread(() -> {
                    assetDecryptionInProgress = false;
                    dismissAssetDecryptionDialog();
                    showLauncher();
                    showMessage("Could not prepare assets. " + exception.getMessage()
                            + " Check that the selected www folder allows writing.");
                });
            }
        });
    }

    private void dismissAssetDecryptionDialog() {
        if (assetDecryptionDialog != null) {
            assetDecryptionDialog.dismiss();
            assetDecryptionDialog = null;
        }
    }

    private DecryptionSummary decryptGameAssets(
            DocumentFile gameFolder, AssetProgressListener progressListener) throws IOException {
        DocumentFile outputRoot = findOrCreateDirectory(gameFolder, DECRYPTED_DIRECTORY);
        ArrayList<AssetSource> sources = new ArrayList<>();
        for (String assetDirectory : new String[]{"audio", "data", "img"}) {
            DocumentFile sourceRoot = findDirectChild(gameFolder, assetDirectory);
            if (sourceRoot != null && sourceRoot.isDirectory()) {
                findOrCreateDirectory(outputRoot, assetDirectory);
                collectAssetFiles(sourceRoot, assetDirectory, sources);
            }
        }
        if (sources.isEmpty()) {
            throw new IOException("No files were found in www/audio, www/data, or www/img.");
        }

        long totalBytes = 0;
        for (AssetSource source : sources) {
            totalBytes += Math.max(1, source.file.length());
        }
        LinkedHashMap<String, DecryptionManifestEntry> manifest = readDecryptionManifest(outputRoot);
        long completedBytes = 0;
        ArrayList<String> failures = new ArrayList<>();
        DecryptionSummary summary = new DecryptionSummary();
        Map<String, DocumentFile> outputDirectories = new HashMap<>();
        outputDirectories.put("", outputRoot);
        long bytesSinceManifestWrite = 0;

        for (int i = 0; i < sources.size(); i++) {
            AssetSource source = sources.get(i);
            long fileBytes = Math.max(1, source.file.length());
            String sourceName = source.file.getName();
            try {
                if (sourceName == null || sourceName.isEmpty()) {
                    throw new IOException("An asset has no filename.");
                }
                String sourceRelative = source.relativeDirectory.isEmpty()
                        ? sourceName
                        : source.relativeDirectory + "/" + sourceName;
                DecryptionManifestEntry existing = manifest.get(sourceRelative);
                if (existing != null
                        && existing.sourceLength == Math.max(1, source.file.length())
                        && existing.sourceLastModified == source.file.lastModified()) {
                    DocumentFile existingOutput = findFile(outputRoot, existing.outputRelative);
                    if (existingOutput != null && existingOutput.isFile()
                            && existingOutput.length() == existing.outputLength
                            && existingOutput.length() > 0) {
                        completedBytes += fileBytes;
                        summary.copied++;
                        reportAssetProgress(progressListener, completedBytes, totalBytes,
                                i + 1, sources.size(), sourceName, "Resuming");
                        continue;
                    }
                }

                DocumentFile outputDirectory = ensureOutputDirectory(
                        outputRoot, source.relativeDirectory, outputDirectories);
                DocumentFile temp = findDirectChild(outputDirectory,
                        ".tcoaal-part-" + i);
                if (temp != null) {
                    temp.delete();
                }
                temp = outputDirectory.createFile("application/octet-stream",
                        ".tcoaal-part-" + i);
                if (temp == null) {
                    throw new IOException("Android could not create a temporary output file.");
                }

                InputStream input = getContentResolver().openInputStream(source.file.getUri());
                if (input == null) {
                    temp.delete();
                    throw new IOException("Android could not open the selected asset.");
                }
                OutputStream output = getContentResolver().openOutputStream(temp.getUri(), "wt");
                if (output == null) {
                    input.close();
                    temp.delete();
                    throw new IOException("Android could not write the temporary output file.");
                }
                LlamaAssetDecryptor.Result result;
                try (InputStream sourceStream = input; OutputStream targetStream = output) {
                    result = LlamaAssetDecryptor.decryptStream(sourceStream, targetStream, sourceName);
                }

                String outputName = sourceName + result.extension;
                String outputRelative = source.relativeDirectory.isEmpty()
                        ? outputName
                        : source.relativeDirectory + "/" + outputName;
                DocumentFile finalOutput = findDirectChild(outputDirectory, outputName);
                if (finalOutput != null && finalOutput.isDirectory()) {
                    temp.delete();
                    throw new IOException("A directory conflicts with output file " + outputName + ".");
                }
                if (finalOutput != null) {
                    finalOutput.delete();
                }
                if (!temp.renameTo(outputName)) {
                    temp.delete();
                    throw new IOException("Android could not finalize output file " + outputName + ".");
                }
                finalOutput = findDirectChild(outputDirectory, outputName);
                if (finalOutput == null || !finalOutput.isFile() || finalOutput.length() <= 0) {
                    throw new IOException("The output file is missing or empty: " + outputName);
                }

                manifest.put(sourceRelative, new DecryptionManifestEntry(
                        source.file.length(), source.file.lastModified(),
                        outputRelative, finalOutput.length()));
                bytesSinceManifestWrite += finalOutput.length();
                if (manifest.size() % 50 == 0 || bytesSinceManifestWrite >= 64L * 1024 * 1024) {
                    writeDecryptionManifest(outputRoot, manifest, false);
                    bytesSinceManifestWrite = 0;
                }
                if (result.encrypted) {
                    summary.decrypted++;
                } else {
                    summary.copied++;
                }
            } catch (IOException | SecurityException exception) {
                failures.add(sourceName + ": " + exception.getMessage());
                Log.w(TAG, "Could not prepare game asset " + source.file.getUri(), exception);
            }

            completedBytes += fileBytes;
            reportAssetProgress(progressListener, completedBytes, totalBytes,
                    i + 1, sources.size(), sourceName == null ? "asset" : sourceName,
                    failures.isEmpty() ? "Processing" : "Retrying");
        }

        if (!failures.isEmpty()) {
            throw new IOException("Failed to prepare " + failures.size() + " asset(s). "
                    + failures.get(0));
        }
        writeDecryptionManifest(outputRoot, manifest, true);
        return summary;
    }

    private void collectAssetFiles(
            DocumentFile sourceDirectory, String relativeDirectory, ArrayList<AssetSource> sources) {
        for (DocumentFile child : sourceDirectory.listFiles()) {
            String name = child.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (child.isDirectory()) {
                String childRelativeDirectory = relativeDirectory.isEmpty()
                        ? name
                        : relativeDirectory + "/" + name;
                collectAssetFiles(child, childRelativeDirectory, sources);
            } else if (child.isFile()) {
                sources.add(new AssetSource(child, relativeDirectory));
            }
        }
    }

    private byte[] readDocument(DocumentFile source) throws IOException {
        InputStream input = getContentResolver().openInputStream(source.getUri());
        if (input == null) {
            throw new IOException("Android could not open the selected asset.");
        }
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32 * 1024];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            return output.toByteArray();
        }
    }

    private void writeDocument(DocumentFile directory, String name, byte[] contents)
            throws IOException {
        DocumentFile target = findDirectChild(directory, name);
        if (target != null && target.isDirectory()) {
            throw new IOException("A directory conflicts with output file " + name + ".");
        }
        if (target == null) {
            target = directory.createFile(mimeTypeFor(name, null), name);
        }
        if (target == null) {
            throw new IOException("Android could not create output file " + name + ".");
        }
        OutputStream output = getContentResolver().openOutputStream(target.getUri(), "wt");
        if (output == null) {
            throw new IOException("Android could not write output file " + name + ".");
        }
        try (OutputStream stream = output) {
            stream.write(contents);
        }
    }

    private DocumentFile ensureOutputDirectory(
            DocumentFile outputRoot,
            String relativeDirectory,
            Map<String, DocumentFile> outputDirectories) throws IOException {
        DocumentFile existing = outputDirectories.get(relativeDirectory);
        if (existing != null) {
            return existing;
        }
        DocumentFile parent = outputRoot;
        String currentPath = "";
        for (String segment : relativeDirectory.split("/")) {
            currentPath = currentPath.isEmpty() ? segment : currentPath + "/" + segment;
            DocumentFile directory = outputDirectories.get(currentPath);
            if (directory == null) {
                directory = findOrCreateDirectory(parent, segment);
                outputDirectories.put(currentPath, directory);
            }
            parent = directory;
        }
        return parent;
    }

    private DocumentFile findOrCreateDirectory(DocumentFile parent, String name)
            throws IOException {
        DocumentFile existing = findDirectChild(parent, name);
        if (existing != null) {
            if (!existing.isDirectory()) {
                throw new IOException("A file conflicts with output directory " + name + ".");
            }
            return existing;
        }
        DocumentFile created = parent.createDirectory(name);
        if (created == null) {
            throw new IOException("Android could not create output directory " + name + ".");
        }
        return created;
    }

    private DocumentFile findDirectChild(DocumentFile parent, String name) {
        if (parent == null || !parent.isDirectory()) {
            return null;
        }
        for (DocumentFile child : parent.listFiles()) {
            if (name.equalsIgnoreCase(child.getName())) {
                return child;
            }
        }
        return null;
    }

    private interface AssetProgressListener {
        void onProgress(AssetProgress progress);
    }

    private static final class AssetProgress {
        final int value;
        final String message;

        AssetProgress(int value, String message) {
            this.value = value;
            this.message = message;
        }
    }

    private static final class AssetSource {
        final DocumentFile file;
        final String relativeDirectory;

        AssetSource(DocumentFile file, String relativeDirectory) {
            this.file = file;
            this.relativeDirectory = relativeDirectory;
        }
    }

    private static final class DecryptionSummary {
        int decrypted;
        int copied;
    }

    private static final class DecryptionManifestEntry {
        final long sourceLength;
        final long sourceLastModified;
        final String outputRelative;
        final long outputLength;

        DecryptionManifestEntry(long sourceLength, long sourceLastModified,
                                String outputRelative, long outputLength) {
            this.sourceLength = sourceLength;
            this.sourceLastModified = sourceLastModified;
            this.outputRelative = outputRelative;
            this.outputLength = outputLength;
        }
    }

    private void reportAssetProgress(AssetProgressListener listener, long completedBytes,
                                     long totalBytes, int completedFiles, int totalFiles,
                                     String fileName, String phase) {
        int value = (int) Math.min(1000, completedBytes * 1000 / Math.max(1, totalBytes));
        listener.onProgress(new AssetProgress(value,
                phase + " asset " + completedFiles + " of " + totalFiles + ": " + fileName));
    }

    private LinkedHashMap<String, DecryptionManifestEntry> readDecryptionManifest(
            DocumentFile outputRoot) throws IOException {
        LinkedHashMap<String, DecryptionManifestEntry> entries = new LinkedHashMap<>();
        DocumentFile manifestFile = findDirectChild(outputRoot, DECRYPTION_MANIFEST);
        if (manifestFile == null || !manifestFile.isFile()) {
            return entries;
        }
        byte[] contents;
        try (InputStream input = getContentResolver().openInputStream(manifestFile.getUri())) {
            if (input == null) {
                throw new IOException("Android could not read the decryption progress file.");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            contents = output.toByteArray();
        }
        String text = new String(contents, StandardCharsets.UTF_8);
        for (String line : text.split("\\n")) {
            if (line.isEmpty() || line.equals(DECRYPTION_COMPLETE_MARKER)
                    || line.startsWith(DECRYPTION_COMPLETE_MARKER + "\t")) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length == 5) {
                try {
                    entries.put(fields[0], new DecryptionManifestEntry(
                            Long.parseLong(fields[1]), Long.parseLong(fields[2]),
                            fields[3], Long.parseLong(fields[4])));
                } catch (NumberFormatException ignored) {
                    Log.w(TAG, "Ignoring an invalid decryption progress entry.");
                }
            }
        }
        return entries;
    }

    private boolean isDecryptionManifestComplete(DocumentFile outputRoot) {
        DocumentFile manifestFile = findDirectChild(outputRoot, DECRYPTION_MANIFEST);
        if (manifestFile == null || !manifestFile.isFile()) {
            return false;
        }
        try (InputStream input = getContentResolver().openInputStream(manifestFile.getUri())) {
            if (input == null) {
                return false;
            }
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
            for (String line : text.split("\\n")) {
                if (line.equals(DECRYPTION_COMPLETE_MARKER)
                        || line.startsWith(DECRYPTION_COMPLETE_MARKER + "\t")) {
                    return true;
                }
            }
        } catch (IOException | SecurityException exception) {
            Log.w(TAG, "Unable to read decryption completion marker.", exception);
        }
        return false;
    }

    private void writeDecryptionManifest(DocumentFile outputRoot,
                                         LinkedHashMap<String, DecryptionManifestEntry> entries,
                                         boolean complete) throws IOException {
        StringBuilder text = new StringBuilder();
        if (complete) {
            text.append(DECRYPTION_COMPLETE_MARKER).append('\t').append(entries.size()).append('\n');
        }
        for (Map.Entry<String, DecryptionManifestEntry> entry : entries.entrySet()) {
            DecryptionManifestEntry value = entry.getValue();
            text.append(entry.getKey()).append('\t')
                    .append(value.sourceLength).append('\t')
                    .append(value.sourceLastModified).append('\t')
                    .append(value.outputRelative).append('\t')
                    .append(value.outputLength).append('\n');
        }
        writeDocument(outputRoot, DECRYPTION_MANIFEST,
                text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String readLanguagePack(DocumentFile assetsRoot) throws IOException {
        DocumentFile file = findFile(assetsRoot, LANGUAGE_DATA_PATH);
        if (file == null || !file.isFile()) {
            throw new IOException("Missing " + LANGUAGE_DATA_PATH);
        }
        try (InputStream stream = getContentResolver().openInputStream(file.getUri())) {
            if (stream == null) {
                throw new IOException("Android could not open " + LANGUAGE_DATA_PATH);
            }
            byte[] signature = new byte[8];
            int signatureLength = 0;
            while (signatureLength < signature.length) {
                int bytesRead = stream.read(signature, signatureLength, signature.length - signatureLength);
                if (bytesRead < 0) {
                    break;
                }
                signatureLength += bytesRead;
            }
            if (signatureLength != signature.length
                    || !"LANGDATA".equals(new String(signature, StandardCharsets.US_ASCII))) {
                throw new IOException("Invalid English language data signature.");
            }
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
            JSONObject data;
            try {
                data = new JSONObject(json);
            } catch (org.json.JSONException exception) {
                throw new IOException("English language data is not valid JSON.", exception);
            }
            JSONObject lines = data.optJSONObject("linesLUT");
            if (lines == null || !lines.has("mDW32lq2")
                    || data.optJSONObject("labelLUT") == null) {
                throw new IOException("English language data is missing its dialogue lookup tables.");
            }
            return json;
        }
    }

    private DocumentFile getSavedFolder(String preferenceKey) {
        String value = getPreferences(MODE_PRIVATE).getString(preferenceKey, null);
        return value == null ? null : DocumentFile.fromTreeUri(this, Uri.parse(value));
    }

    private boolean isSupportedGameFolder(DocumentFile root) {
        return root != null && getMissingGameFiles(root).isEmpty();
    }

    private boolean isSupportedAssetsFolder(DocumentFile root) {
        return root != null && getMissingAssetsFiles(root).isEmpty();
    }

    private ArrayList<String> getMissingGameFiles(DocumentFile root) {
        ArrayList<String> missingFiles = new ArrayList<>();
        try {
            if (root == null || !root.isDirectory()) {
                missingFiles.add("The selected folder is unavailable or is not a directory.");
                return missingFiles;
            }
            if (findChild(root, "index.html") == null) {
                missingFiles.add("index.html");
            }
            DocumentFile js = findChild(root, "js");
            if (js == null || findChild(js, "rpg_core.js") == null) {
                missingFiles.add("js/rpg_core.js");
            }
            if (!missingFiles.isEmpty() && findChild(root, "www") != null) {
                missingFiles.add("A www subfolder was found. Select that subfolder instead.");
            }
        } catch (SecurityException exception) {
            Log.w(TAG, "Unable to inspect the selected game folder.", exception);
            missingFiles.clear();
            missingFiles.add("Android could not read this folder. Select it again and grant access.");
        }
        return missingFiles;
    }

    private ArrayList<String> getMissingAssetsFiles(DocumentFile root) {
        ArrayList<String> missingFiles = new ArrayList<>();
        try {
            if (root == null || !root.isDirectory()) {
                missingFiles.add("Select the decrypted TCOAAL assets folder.");
                return missingFiles;
            }
            if (findChild(root, "data") == null
                    || findHashedAsset(root, "data/System.json") == null) {
                missingFiles.add("data assets (including the hashed System.json file)");
            }
            if (findChild(root, "audio") == null) {
                missingFiles.add("audio folder");
            }
            if (findChild(root, "img") == null) {
                missingFiles.add("img folder");
            }
            if (findFile(root, LANGUAGE_DATA_PATH) == null) {
                missingFiles.add("English language data (data/9c7050ae76645487)");
            }
            DocumentFile manifest = findDirectChild(root, DECRYPTION_MANIFEST);
            boolean internallyGenerated = DECRYPTED_DIRECTORY.equalsIgnoreCase(root.getName());
            if ((manifest != null || internallyGenerated) && !isDecryptionManifestComplete(root)) {
                missingFiles.add("Complete asset preparation (previous attempt was interrupted)");
            }
        } catch (SecurityException exception) {
            Log.w(TAG, "Unable to inspect the selected decrypted assets folder.", exception);
            missingFiles.clear();
            missingFiles.add("Android could not read this folder. Select it again and grant access.");
        }
        return missingFiles;
    }

    private DocumentFile findChild(DocumentFile parent, String name) {
        if (parent == null || !parent.isDirectory()) {
            return null;
        }
        Map<String, DocumentFile> children = directoryChildren.computeIfAbsent(
                parent.getUri().toString(),
                ignored -> indexDirectory(parent));
        return children.get(name.toLowerCase(Locale.ROOT));
    }

    private Map<String, DocumentFile> indexDirectory(DocumentFile directory) {
        Map<String, DocumentFile> children = new HashMap<>();
        for (DocumentFile child : directory.listFiles()) {
            String name = child.getName();
            if (name != null) {
                children.putIfAbsent(name.toLowerCase(Locale.ROOT), child);
            }
        }
        return children;
    }

    private DocumentFile findHashedAsset(DocumentFile root, String relativePath) {
        String[] segments = relativePath.split("/");
        if (segments.length == 0) {
            return null;
        }
        String hashedName = hashAssetPath(relativePath) + extensionOf(segments[segments.length - 1]);
        segments[segments.length - 1] = hashedName;
        StringBuilder hashedPath = new StringBuilder();
        for (String segment : segments) {
            if (hashedPath.length() > 0) {
                hashedPath.append('/');
            }
            hashedPath.append(segment);
        }
        return findFile(root, hashedPath.toString());
    }

    private String hashAssetPath(String relativePath) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(relativePath.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                int value = digest[i] & 0xff;
                hash.append(HEX_DIGITS[value >>> 4]).append(HEX_DIGITS[value & 0x0f]);
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to resolve decrypted game assets.", exception);
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot);
    }

    private DocumentFile findFile(DocumentFile root, String relativePath) {
        DocumentFile file = root;
        for (String segment : relativePath.split("/")) {
            file = findChild(file, segment);
            if (file == null) {
                return null;
            }
        }
        return file;
    }

    private void showUnsupportedFolder(int requestCode, ArrayList<String> missingFiles) {
        StringBuilder message = new StringBuilder();
        message.append(requestCode == PICK_GAME_FOLDER
                ? "This folder can't be used as the game's www folder:\n\n"
                : "This folder can't be used as the decrypted assets folder:\n\n");
        for (String missing : missingFiles) {
            message.append("\u2022 ").append(missing).append('\n');
        }
        message.append(requestCode == PICK_GAME_FOLDER
                ? "\nSelect the original www folder containing index.html and js/rpg_core.js."
                : "\nSelect the folder containing the decrypted data, audio, and img folders.");
        new AlertDialog.Builder(this)
                .setTitle("Can't use this game folder")
                .setMessage(message.toString())
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void launchGame(DocumentFile root, DocumentFile assetsRoot) {
        gameRoot = root;
        decryptedAssetsRoot = assetsRoot;
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (message.message().startsWith("[MobileTextFix]")) {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e(TAG, "WebView text patch: " + message.message());
                        runOnUiThread(() -> showMessage(message.message()));
                    } else {
                        Log.i(TAG, "WebView text patch: " + message.message());
                    }
                    return true;
                }
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.e(TAG, "WebView error at " + message.sourceId() + ":"
                            + message.lineNumber() + ": " + message.message());
                }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                applyVirtualControls(currentControlMask);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri requestUri = request.getUrl();
                if (!"https".equals(requestUri.getScheme())
                        || !"game.local".equals(requestUri.getHost())) {
                    return super.shouldInterceptRequest(view, request);
                }
                return serveGameFile(requestUri.getPath());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri requestUri = request.getUrl();
                return !"https".equals(requestUri.getScheme())
                        || !"game.local".equals(requestUri.getHost());
            }
        });

        gameContainer = new FrameLayout(this);
        gameContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        controlLayout = loadControlLayout();
        controlsView = new VirtualControlsView(this, new VirtualControlsView.Listener() {
            @Override
            public void onControlsChanged(int controlMask) {
                applyVirtualControls(controlMask);
            }

            @Override
            public void onLayoutChanged(VirtualControlsView.Layout layout) {
                saveControlLayout(layout);
            }
        }, controlLayout, false);
        gameContainer.addView(controlsView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(gameContainer);
        webView.loadUrl(GAME_ORIGIN + "/index.html");
    }

    private void applyVirtualControls(int controlMask) {
        currentControlMask = controlMask;
        if (webView == null) {
            return;
        }
        String script = "window.__mobileControlsMask=" + controlMask + ";"
                + "if(window.Input&&Input._currentState){"
                + "var s=Input._currentState,m=window.__mobileControlsMask;"
                + "s.left=!!(m&1);s.right=!!(m&2);s.up=!!(m&4);s.down=!!(m&8);"
                + "s.ok=!!(m&16);s.escape=!!(m&32);s.shift=!!(m&64);"
                + "s.pageup=!!(m&128);s.pagedown=!!(m&256);}";
        webView.post(() -> {
            if (webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private WebResourceResponse serveGameFile(String path) {
        String safePath = normalizePath(path);
        if (safePath == null || gameRoot == null) {
            return notFound();
        }

        try {
            if (MOBILE_PLUGIN_PATH.equals(safePath)) {
                return new WebResourceResponse(
                        "application/javascript",
                        "UTF-8",
                        new ByteArrayInputStream(
                                buildMobileLanguageScript().getBytes(StandardCharsets.UTF_8)));
            }
            if (MOBILE_LANGUAGE_PATH.equals(safePath)) {
                if (languagePackJson == null) {
                    return notFound();
                }
                return new WebResourceResponse(
                        "application/json",
                        "UTF-8",
                        new ByteArrayInputStream(
                                languagePackJson.getBytes(StandardCharsets.UTF_8)));
            }
            DocumentFile file = findResourceFile(safePath);
            if (file == null || !file.isFile()) {
                return notFound();
            }
            InputStream stream = openCachedAsset(file);
            if (stream == null) {
                return notFound();
            }
            if ("js/plugins.js".equals(safePath)) {
                return addMobileLanguagePlugin(stream);
            }
            String mimeType = mimeTypeFor(safePath, file.getType());
            return new WebResourceResponse(mimeType, "UTF-8", stream);
        } catch (IOException | SecurityException exception) {
            Log.e(TAG, "Failed to read selected game asset: " + safePath, exception);
            return new WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    500,
                    "File Read Failed",
                    null,
                    new ByteArrayInputStream(
                            "Unable to read selected game file.".getBytes(StandardCharsets.UTF_8)));
        }
    }

    private InputStream openCachedAsset(DocumentFile sourceFile) throws IOException {
        File cacheDirectory = new File(getCacheDir(), "game-assets");
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs()) {
            Log.w(TAG, "Asset cache unavailable; reading from selected folder.");
            return getContentResolver().openInputStream(sourceFile.getUri());
        }

        String cacheName = sha256Hex(sourceFile.getUri().toString()) + ".cache";
        File cachedFile = new File(cacheDirectory, cacheName);
        if (cachedFile.isFile()) {
            cachedFile.setLastModified(System.currentTimeMillis());
            return new FileInputStream(cachedFile);
        }

        long startTime = System.nanoTime();
        long sourceLength = sourceFile.length();
        if (sourceLength > MAX_CACHED_FILE_BYTES) {
            return getContentResolver().openInputStream(sourceFile.getUri());
        }

        InputStream source = getContentResolver().openInputStream(sourceFile.getUri());
        if (source == null) {
            return null;
        }

        File temporaryFile = File.createTempFile("asset-", ".tmp", cacheDirectory);
        long copiedBytes = 0;
        try {
            try (InputStream input = source;
                 OutputStream output = new FileOutputStream(temporaryFile)) {
                byte[] buffer = new byte[32 * 1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    copiedBytes += bytesRead;
                    if (copiedBytes > MAX_CACHED_FILE_BYTES) {
                        temporaryFile.delete();
                        return getContentResolver().openInputStream(sourceFile.getUri());
                    }
                }
            }

            synchronized (assetCacheLock) {
                trimAssetCache(cacheDirectory, temporaryFile.length());
                if (!temporaryFile.renameTo(cachedFile)) {
                    if (cachedFile.isFile()) {
                        temporaryFile.delete();
                    } else {
                        Log.w(TAG, "Unable to keep cached asset; reading it from the selected folder.");
                        temporaryFile.delete();
                        return getContentResolver().openInputStream(sourceFile.getUri());
                    }
                }
                cachedFile.setLastModified(System.currentTimeMillis());
            }
            logSlowAssetRead(sourceFile, startTime);
            return new FileInputStream(cachedFile);
        } catch (IOException exception) {
            temporaryFile.delete();
            Log.w(TAG, "Unable to cache an asset; reading it from the selected folder.", exception);
            return getContentResolver().openInputStream(sourceFile.getUri());
        }
    }

    private void logSlowAssetRead(DocumentFile file, long startTime) {
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        if (elapsedMs >= 250) {
            Log.i(TAG, "Cached asset " + file.getName() + " in " + elapsedMs + " ms.");
        }
    }

    private void trimAssetCache(File cacheDirectory, long incomingBytes) {
        File[] files = cacheDirectory.listFiles(File::isFile);
        if (files == null) {
            return;
        }
        long staleTemporaryFileCutoff = System.currentTimeMillis() - 60L * 60 * 1000;
        long totalBytes = 0;
        for (File file : files) {
            if (file.getName().endsWith(".tmp")) {
                if (file.lastModified() < staleTemporaryFileCutoff) {
                    file.delete();
                }
            } else {
                totalBytes += file.length();
            }
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (totalBytes + incomingBytes <= ASSET_CACHE_LIMIT_BYTES) {
                break;
            }
            if (file.getName().endsWith(".tmp")) {
                continue;
            }
            long fileLength = file.length();
            if (file.delete()) {
                totalBytes -= fileLength;
            }
        }
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                int unsigned = item & 0xff;
                result.append(HEX_DIGITS[unsigned >>> 4]).append(HEX_DIGITS[unsigned & 0x0f]);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to cache selected game assets.", exception);
        }
    }

    private WebResourceResponse addMobileLanguagePlugin(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = stream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        String plugins = new String(output.toByteArray(), StandardCharsets.UTF_8);
        int arrayEnd = plugins.lastIndexOf("];");
        if (arrayEnd < 0 || !plugins.contains("var $plugins")) {
            throw new IOException("The game's plugin list has an unsupported format.");
        }
        String descriptor = "{\"name\":\"MobileTextFix\",\"status\":true,"
                + "\"description\":\"Mobile English dialogue support\",\"parameters\":{}},\n";
        String patchedPlugins = plugins.substring(0, arrayEnd)
                + descriptor
                + plugins.substring(arrayEnd);
        return new WebResourceResponse(
                "application/javascript",
                "UTF-8",
                new ByteArrayInputStream(patchedPlugins.getBytes(StandardCharsets.UTF_8)));
    }

    private String buildMobileLanguageScript() {
        return "(function(){"
                + "var ready=false;"
                + "var originalIsReady=Scene_Boot.prototype.isReady;"
                + "Scene_Boot.prototype.isReady=function(){"
                + "return ready&&originalIsReady.apply(this,arguments);};"
                + "fetch('/" + MOBILE_LANGUAGE_PATH + "').then(function(response){"
                + "if(!response.ok)throw new Error('HTTP '+response.status);"
                + "return response.json();"
                + "}).then(function(data){"
                + "if(!data||!data.labelLUT||!data.linesLUT"
                + "||!data.linesLUT.mDW32lq2)"
                + "throw new Error('English dialogue lookup tables are missing');"
                + "var labels=data.labelLUT,lines=data.linesLUT;"
                + "function translate(text){"
                + "text=String(text);"
                + "text=text.replace(/\\(label\\)\\[([^\\]]+)\\]/g,function(match,id){"
                + "return labels[id]||match;});"
                + "return text.replace(/\\(lines\\)\\[([^\\]]+)\\]/g,function(match,id,offset){"
                + "return Array.isArray(lines[id])?"
                + "(offset>0?'\\n':'')+lines[id].join('\\n'):match;});}"
                + "var add=Game_Message.prototype.add;"
                + "Game_Message.prototype.add=function(text){"
                + "return add.call(this,translate(text));};"
                + "var choices=Game_Message.prototype.setChoices;"
                + "Game_Message.prototype.setChoices=function(){"
                + "var args=Array.prototype.slice.call(arguments);"
                + "if(Array.isArray(args[0]))args[0]=args[0].map(translate);"
                + "return choices.apply(this,args);};"
                + "ready=true;"
                + "console.info('[MobileTextFix] English dialogue data loaded');"
                + "}).catch(function(error){"
                + "console.error('[MobileTextFix] Could not load English dialogue data: '+error.message);"
                + "});"
                + "})();";
    }

    private DocumentFile findResourceFile(String relativePath) {
        if (relativePath.startsWith("data/")
                || relativePath.startsWith("audio/")
                || relativePath.startsWith("img/")) {
            DocumentFile decryptedFile = findHashedAsset(decryptedAssetsRoot, relativePath);
            if (decryptedFile != null && decryptedFile.isFile()) {
                return decryptedFile;
            }
            decryptedFile = findFile(decryptedAssetsRoot, relativePath);
            if (decryptedFile != null && decryptedFile.isFile()) {
                return decryptedFile;
            }
        }
        return findFile(gameRoot, relativePath);
    }

    private String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return "index.html";
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return normalized;
    }

    private String mimeTypeFor(String path, String providerMimeType) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                extension.toLowerCase(Locale.ROOT));
        if (mimeType != null) {
            return mimeType;
        }
        return providerMimeType == null ? "application/octet-stream" : providerMimeType;
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                404,
                "Not Found",
                null,
                new ByteArrayInputStream(new byte[0]));
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Return to the launcher? Save your game first if needed.")
                .setNegativeButton("Stay", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Return", (dialog, which) -> showLauncher())
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
