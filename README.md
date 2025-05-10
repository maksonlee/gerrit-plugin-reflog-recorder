# 🧩 gerrit-plugin-reflog-recorder

## 🔧 Purpose

`gerrit-plugin-reflog-recorder` is a Gerrit plugin that tracks Git reference updates in a reflog-like fashion.  
It stores these updates in an embedded H2 database and provides SSH commands to:

- Query historical revision states at a given timestamp.
- Generate static manifests based on historical Git states.

---

## ✅ Key Features

### 1. Reflog Tracking
- Listens to `GitReferenceUpdatedListener` events.
- Records:
    - `project`, `ref`, `oldRev`, `newRev`, `timestamp`, `user`
- Stores entries in embedded H2 database (UTC).
- Deduplicates by `(project, ref, newRev)`.

### 2. SSH Commands
- `get-reflog`: Get the revision of a ref at a specific timestamp.
- `import-native-reflog`: Import native reflog entries via JGit.
- `resolve-manifest`: Rewrite `default.xml` using reflog data.

### 3. Manifest Output Enhancements
- Removes `<superproject>` and `<contactinfo>`.
- Skips projects with `groups="notdefault"`.
- Output is compatible with `repo manifest -r -o static.xml`.

---

## 🔍 Why Not Use Gerrit's Built-in Reflog API?

Gerrit has an endpoint:

```
GET /projects/{project}/branches/{branch}/reflog
```

But it has major limitations:
- Depends on Git reflog being enabled (not default with JGit).
- Limited to recent entries, no timestamp-based queries.
- File-based and susceptible to garbage collection.

**This plugin adds:**
- Persistent H2-based tracking
- Timestamp lookup
- Manifest rewriting
- Queryable database history

---

## 🏗️ Architecture Overview

| Layer              | Role                                       |
|--------------------|--------------------------------------------|
| `ReflogRepository` | Low-level DB operations                    |
| `ReflogService`    | Business logic (ref resolution, XML parsing) |
| `ManifestResolver` | DOM parser and manifest patcher            |
| SSH Commands       | CLI interface for administrators           |
| `ReflogRecorder`   | Gerrit listener for Git ref updates        |

---

## 🚀 Installation

1. **Clone this repository**:
   ```bash
   git clone https://github.com/maksonlee/gerrit-plugin-reflog-recorder.git
   ```

2. **Build the plugin with Maven**:
   ```bash
   cd gerrit-plugin-reflog-recorder
   mvn package
   ```

3. **Deploy to Gerrit**:
   ```bash
   cp target/reflog-recorder.jar $GERRIT_SITE/plugins/
   sudo systemctl restart gerrit
   ```

---

## 🧪 Usage Examples

- **Get Reflog**:
   ```bash
   ssh -p 29418 user@host gerrit get-reflog      --project my-project      --ref refs/heads/main      --timestamp 2025-05-11T12:00:00Z
   ```

- **Import Native Reflog**:
   ```bash
   ssh -p 29418 user@host gerrit import-native-reflog
   ```

- **Resolve Manifest**:
   ```bash
   ssh -p 29418 user@host gerrit resolve-manifest      --timestamp 2025-05-11T12:00:00Z
   ```

---

## 🧪 Example: Sync AOSP 15 with Reflog-Based Manifest

```bash
mkdir aosp-15
cd aosp-15

# Initialize with the official manifest repo and branch
repo init -u git://gerrit/platform/manifest -b android-15.0.0_r30

# Override manifest with a timestamp-pinned version
ssh -p 29418 maksonlee@gerrit reflog-recorder resolve-manifest   platform/manifest   refs/heads/android-15.0.0_r30   2025-05-04T08:30:00Z > .repo/manifest.xml

# Sync exact historical state
repo sync -c
```

This will fetch the exact commits from all projects as they were on `android-15.0.0_r30` at 2025-05-04 08:30 UTC.

---

## 🧠 Future Enhancements

- Add REST API support.
- Add automated tests (unit and integration)

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## 🤝 Contributing

Contributions are welcome! Please open issues or pull requests for improvements or bug fixes.