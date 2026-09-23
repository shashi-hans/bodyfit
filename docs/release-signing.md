# Release signing

The build is wired for signing, but the key itself is not in this repo and must not be.

## Why you create the key, not a tool or an assistant

The upload key is the only proof that an update to Body Fit came from you. Two facts follow:

- **Lose it and you cannot update the app again** under the same listing, short of asking
  Google to reset your upload key. Your users would otherwise have to uninstall and
  reinstall, losing their history, because this app keeps everything on the device.
- **Leak the password and someone else can sign as you.**

So the password has to be chosen by you and stored in your password manager, never typed
into a chat, a commit, a log, or a build script.

## Create the key

Run this once, from anywhere outside the repo. It will prompt for a password; use a strong
one and save it in your password manager immediately.

```sh
keytool -genkeypair -v \
  -keystore ~/keys/bodyfit-upload.jks \
  -alias bodyfit-upload \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=Shashi Hans, O=Body Fit, C=IN"
```

`-validity 10000` is about 27 years. Play requires a key valid well past 22 October 2033.

Back the file up somewhere you will still have in five years. A password manager attachment
or an encrypted drive both work. A single copy on one laptop does not.

## Point the build at it

Create `keystore.properties` in the repo root. It is gitignored, and `.gitignore` also
covers `*.jks` and `*.keystore`.

```properties
storeFile=/home/<you>/keys/bodyfit-upload.jks
storePassword=<the password you chose>
keyAlias=bodyfit-upload
keyPassword=<the same password, unless you set a separate key password>
```

CI can supply the same four values as environment variables instead, so no file is needed
on a build agent:

`BODYFIT_STORE_FILE`, `BODYFIT_STORE_PASSWORD`, `BODYFIT_KEY_ALIAS`, `BODYFIT_KEY_PASSWORD`.

When neither the file nor the variables are present, a release build still succeeds and is
simply unsigned. A fresh clone builds without needing your secrets.

## Build what Play wants

```sh
./gradlew bundleRelease
# app/build/outputs/bundle/release/app-release.aab
```

Upload the `.aab`, not an APK.

## Check it worked

```sh
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

The certificate DN should be yours. If the build produced `app-release-unsigned.apk`
instead, the four values above were not picked up.

## Play App Signing

Play will offer to manage the app signing key for you, with the key you created above
becoming the *upload* key. Accept it. It means a lost upload key can be reset rather than
ending the listing. You still must not lose it, but it stops being fatal.
