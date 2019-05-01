package com.f8jmusic.neurolinkbuilder;

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract;

import com.f8jmusic.uroborostlib.UroborosTRuntime;
import com.google.common.base.Strings;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.f8jmusic.uroborostlib.UroborosTRuntime.copyFile;

public class DatabaseLoader {

    static Uri uriInWork = null;
    private static int Shown = 0;
    private static int Learned = 0;
    private static int Available = 0;
    private static String filename = null;
    private static Set<String> terms_self_test_set = new TreeSet<String>();
    // Currently load term to study
    private static String currentTerm = null;
    // Actual database: Map of Term to Studied (times), Learned (yes/no)
    private static Map<String, TermData> dictionary = new TreeMap<String, TermData>();
    // Gets filled once the round is finished from the dictionary map
    private static Map<String, TermData> current = new TreeMap<String, TermData>();

    private DatabaseLoader() {
    }

    public static String getFilename() {
        return filename;
    }

    public static void setCurrentTermMeaning(String new_meaning) {
        if (StringUtils.isEmpty(filename) || StringUtils.isEmpty(new_meaning))
            return;

        current.get(DatabaseLoader.currentTerm).definition = new_meaning;
    }

    // _filename can be URI
    public static DatabaseLoader GetDatabaseLoader(final String _db_filename) throws IOException, DatabaseLoaderException {
        CleanUp();

        String NLB_NAME = UroborosTRuntime.combinePaths(true, Environment.getExternalStorageDirectory().getAbsolutePath(), "NLB/dics");

        if (_db_filename.contains(NLB_NAME)) {
            filename = _db_filename;

            try {
                loadAllTerms();
            } catch (IOException ex) {
                new File(_db_filename).delete();
                throw ex;
            } catch (DatabaseLoaderException ex) {
                new File(_db_filename).delete();
                throw ex;
            } catch (Exception ex) {
                throw ex; // not deleting anything in this case
            }

            if (current.isEmpty())
                throw new DatabaseLoaderException("You seem to learn everything from this database, please load another one or re-process this one");

            return new DatabaseLoader();
        }

        uriInWork = Uri.fromFile(new File(_db_filename));

        // Following is valid for all non NLB folder loads
        if (!new File(_db_filename).exists()) {

            filename = UroborosTRuntime.getFileNameFromURI(uriInWork);
            if (Strings.isNullOrEmpty(filename)) {
                final Uri fileUriAlternative = Uri.parse(_db_filename);
                filename = UroborosTRuntime.getFileNameFromURI(fileUriAlternative);
                if (Strings.isNullOrEmpty(filename)) {
                    throw new DatabaseLoaderException(String.format("Uri %s couldn't be converted into a valid file name", _db_filename));
                }
                uriInWork = fileUriAlternative;
            }
        } else
            filename = _db_filename;

        final String finalDBFileName = UroborosTRuntime.combinePaths(true, Environment.getExternalStorageDirectory().getAbsolutePath(), "NLB/dics", new File(filename).getName());

        if (!_db_filename.equals(finalDBFileName)) {
            if (new File(finalDBFileName).exists()) { // if the file is not in the NLB folder but the file with same name already exists and we need to decide if we do copy over
                UroborosTRuntime.userQuestion(
                        "Inquiry",
                        "This database already exists, are you sure to overwrite it?",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) { // YES
                                try {
                                    copyFile(uriInWork, finalDBFileName);
                                } catch (IOException e) {
                                    UroborosTRuntime.LOG_FATAL(this.getClass(), e);
                                }
                                ((MainActivity) (UroborosTRuntime.getMainActivityReference())).LoadAndDisplay(finalDBFileName);
                            }
                        },
                        null);

                throw new DatabaseLoaderException("This database already exists"); // not changing the UI while waiting for the response
            } else {
                copyFile(uriInWork, finalDBFileName);
                return GetDatabaseLoader(finalDBFileName);
            }
        }

        throw new DatabaseLoaderException("The program reached an un-reachable execution point");
    }

    private static void CleanUp() {
        dictionary.clear();
        current.clear();
        terms_self_test_set.clear();

        Shown = 0;
        Available = 0;
        Learned = 0;

        filename = null;

        System.gc();
    }

    private static void loadAllTerms() throws IOException, DatabaseLoaderException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;

        checkIfValidDB(br);

        while ((line = br.readLine()) != null) {
            String term = "", definition = "", timesStudied = "", isLearned = "", flag = "";

            term = line.trim();
            if (term.isEmpty())
                continue;

            if ((line = br.readLine()) != null) {
                definition = line.trim();
                if (definition.isEmpty()) {
                    CleanUp();
                    throw new IOException(String.format("Can't read the definition for %s from database", term));
                }
            } else {
                CleanUp();
                throw new IOException(String.format("Can't read the definition for %s from database", term));
            }

            TermData termData = new DatabaseLoader.TermData(definition, term);

            if ((line = br.readLine()) != null) {
                timesStudied = line.trim();
                if (timesStudied.isEmpty()) {
                    CleanUp();
                    throw new IOException(String.format("Can't read the timesStudied for %s from database", term));
                }
            } else {
                CleanUp();
                throw new IOException(String.format("Can't read the timesStudied for %s from database", term));
            }

            if ((line = br.readLine()) != null) {
                isLearned = line.trim();
                if (isLearned.isEmpty()) {
                    CleanUp();
                    throw new IOException(String.format("Can't read the isLearned for %s from database", term));
                }
            } else {
                CleanUp();
                throw new IOException(String.format("Can't read the isLearned for %s from database", term));
            }

            if ((line = br.readLine()) != null) {
                flag = line.trim();
                if (flag.isEmpty()) {
                    CleanUp();
                    throw new IOException(String.format("Can't read the flag for %s from database", term));
                }
            } else {
                CleanUp();
                throw new IOException(String.format("Can't read the flag for %s from database", term));
            }

            termData.timesStudied = Integer.decode(timesStudied);
            termData.isLearned = Boolean.parseBoolean(isLearned);
            termData.flag = flag.toUpperCase();

            dictionary.put(term, termData);
        }
        br.close();

        PrepareForLearning(dictionary, current);
    }

    public static boolean ifKeyAlreadyExists(String candidate) {
        if (dictionary.containsKey(candidate))
            return true;

        return false;
    }

    private static void PrepareForLearning(Map<String, TermData> dictionary, Map<String, TermData> current) {
        for (String key : dictionary.keySet()) {
            if (!dictionary.get(key).flag.contains("SHOWN"))
                current.put(key, dictionary.get(key));
        }

        if (current.isEmpty()) {
            for (String key : dictionary.keySet()) {
                if (!dictionary.get(key).isLearned && !dictionary.get(key).flag.contains(".FLAG"))
                    current.put(key, dictionary.get(key));
            }
        }

        if (current.isEmpty()) {
            for (String key : dictionary.keySet()) {
                if (dictionary.get(key).flag.contains(UroborosTRuntime.SHOWN_STRING) && !dictionary.get(key).flag.contains(".FLAG")) {
                    dictionary.get(key).flag = dictionary.get(key).flag.replace(UroborosTRuntime.SHOWN_STRING, "");
                    current.put(key, dictionary.get(key));
                }
            }
        }

        terms_self_test_set.clear();
    }

    private static void checkIfValidDB(BufferedReader br) throws IOException, DatabaseLoaderException {
        String line;
        if ((line = br.readLine()) != null) {
            if (line.length() < 31 || !line.substring(0, 30).equals(UroborosTRuntime.NLB_DATABASE_INDICE)) {
                throw new DatabaseLoaderException("Corrupted database, please choose another database file");
            }
        } else
            throw new DatabaseLoaderException("Corrupted database, please choose another database file");

        MainActivity.currentDictionaryName = line.substring(30).
                replace("\"", "").
                replace(":", "").
                replace("-", "").
                trim();
    }

    public static void setCurrentTermTitle(String new_title) {
        if (StringUtils.isEmpty(filename) || StringUtils.isEmpty(new_title))
            return;

        TermData termData = getAnswer();

        if (dictionary.containsKey(currentTerm)) {
            dictionary.remove(new_title);
            dictionary.remove(currentTerm);
            dictionary.put(new_title, termData);
        }

        if (current.containsKey(currentTerm)) {
            current.remove(new_title);
            current.remove(currentTerm);
            current.put(new_title, termData);
        }

        currentTerm = new_title;
        termData.term = new_title;
    }

    public int getShown() {
        return DatabaseLoader.Shown;
    }

    public int getLearned() {
        return DatabaseLoader.Learned;
    }

    public int getAvailable() {
        return DatabaseLoader.Available;
    }

    public String getActualStats() {
        if (dictionary != null && !dictionary.isEmpty())
            return String.format("Shown: %s items; Learned: %s items; Available: %s items", getShown(), getLearned(), getAvailable());
        else
            return "You seem to learn everything from this database, please load another one or re-process this one";
    }

    public static boolean isLoaded() {
        return (dictionary != null && !dictionary.isEmpty()) && (current != null && !current.isEmpty());
    }

    public static TermData getAnswer() {
        if (isLoaded()) {
            return current.get(currentTerm);
        } else
            return new TermData("No database is currently loaded");
    }

    public void setResponse(Response response) throws IOException, DatabaseLoaderException {
        TermData termData = current.get(currentTerm);

        if (response == Response.YES) {
            termData.timesStudied++;
            termData.isLearned = true;
            current.remove(currentTerm);

            if (terms_self_test_set.contains(currentTerm))
                throw new IllegalStateException(String.format("A term \'%s\' was already shown", currentTerm));
            else
                terms_self_test_set.add(currentTerm);

            if (!termData.flag.contains("SHOWN"))
                termData.flag = String.format("%s.%s", termData.flag, "SHOWN");

            Learned++;
        } else if (response == Response.NO) {
            termData.timesStudied++;
            current.remove(currentTerm);

            if (terms_self_test_set.contains(currentTerm))
                throw new IllegalStateException(String.format("A term \'%s\' was already shown", currentTerm));
            else
                terms_self_test_set.add(currentTerm);

            if (!termData.flag.contains("SHOWN"))
                termData.flag = String.format("%s.%s", termData.flag, "SHOWN");
        } else if (response == Response.PASS) { // response = PASS
            // Nothing to do, it's a temporary skipping of the term
        } else if (response == Response.FLAG) {
            termData.flag = String.format("%s.%s", termData.flag, "FLAG");
            current.remove(currentTerm);

            if (terms_self_test_set.contains(currentTerm))
                throw new IllegalStateException(String.format("A term \'%s\' was already shown", currentTerm));
            else
                terms_self_test_set.add(currentTerm);

            if (!termData.flag.contains("SHOWN"))
                termData.flag = String.format("%s.%s", termData.flag, "SHOWN");
        } else
            throw new IllegalStateException("A response outside of permitted values encountred");

        saveDatabase();

        Available = current.size();
        Available = current.size();
    }

    public TermData getNextTerm() {
        if (!current.isEmpty()) {
            Random random = new Random();
            List<String> keys = new ArrayList<String>(current.keySet());
            String randomKey = keys.get(random.nextInt(keys.size()));

            if (current.size() > 1) {
                // Radical but working solution on never getting the same term if they are more than 2
                while (randomKey == currentTerm) {
                    randomKey = keys.get(random.nextInt(keys.size()));
                }
            }

            TermData termData = current.get(randomKey);

            termData.timesStudied++;

            Shown++;
            Available = current.size();

            currentTerm = randomKey;

            return termData;
        } else {
            CleanUp();
            return new TermData("Internal error occured");
        }
    }

    private void saveDatabase() throws IOException, DatabaseLoaderException {
        BufferedWriter br = new BufferedWriter(new FileWriter(filename));
        br.write(UroborosTRuntime.NLB_DATABASE_INDICE + " : " + MainActivity.currentDictionaryName);
        br.newLine();
        for (String key : dictionary.keySet()) {
            br.write(dictionary.get(key).term);
            br.newLine();
            br.write(dictionary.get(key).definition);
            br.newLine();
            br.write(Integer.toString(dictionary.get(key).timesStudied));
            br.newLine();
            br.write(Boolean.toString(dictionary.get(key).isLearned));
            br.newLine();
            br.write(dictionary.get(key).flag);
            br.newLine();
        }
        br.flush();
        br.close();

        PrepareForLearning(dictionary, current);

        Available = current.size();
    }

    public static class DatabaseLoaderException extends Exception {
        public DatabaseLoaderException(String message) {
            super(message);
        }
    }

    static final class TermData {
        String term;
        String definition;
        int timesStudied = 0;
        boolean isLearned = false;
        String flag = null;

        TermData(String abnormalMessage) {
            this.term = this.definition = abnormalMessage;
        }

        public TermData(String definition, String term) {
            this.term = term;
            this.definition = definition;
        }

        boolean isMathML() {
            return this.flag.contains(".MATHML");
        }
    }
}
