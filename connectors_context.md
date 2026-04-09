# Jenkins Connectors Plugin — Implementation Context

## Purpose
A standalone "Connectors" management system for Jenkins that lets users define reusable external connection configurations (SCM repos, databases) and bind them to pipelines. Mirrors the Jenkins Credentials plugin UX pattern.

---

## Package Layout

```
io.jenkins.plugins.connectors
├── ConnectorParameterDefinition.java      # Build parameter to select a connector
├── ConnectorParameterValue.java           # Value wrapper
├── WithConnectorStep.java                 # Pipeline step: withConnector('id') { ... }
├── domain/
│   ├── Connector.java                     # Abstract base (AbstractDescribableImpl<Connector>)
│   ├── ConnectorDescriptor.java           # (inner class of Connector)
│   ├── ScmConnector.java                  # Git/SCM connector type
│   └── DbConnector.java                   # Database connector type
├── storage/
│   ├── GlobalConnectorStorage.java        # @Extension Saveable → JENKINS_HOME/connectors.xml
│   ├── FolderConnectorProperty.java       # AbstractFolderProperty → saved in folder config.xml
│   ├── ConnectorResolver.java             # Walks parent chain to find connector by ID
│   └── ConnectorPermissions.java          # Custom permissions (ConnectorView/Create/Update/Delete)
└── ui/
    ├── ConnectorManagementAction.java     # Main CRUD action (Action interface)
    ├── ConnectorRootAction.java           # ManagementLink → /connectors global entry point
    └── ConnectorFolderActionFactory.java  # TransientActionFactory<AbstractFolder> → folder entry
```

### Resources layout mirrors Java packages under `src/main/resources/`.

---

## Domain Model

```java
// Connector.java — abstract base
public abstract class Connector extends AbstractDescribableImpl<Connector> {
    private final String id;        // required, no spaces, [word\-\.]+ pattern
    private final String name;
    private final String description;
    public static abstract class ConnectorDescriptor extends Descriptor<Connector> {
        public abstract String getIconClassName();
    }
}

// ScmConnector.java
@DataBoundConstructor
public ScmConnector(String id, String name, String description,
                    String scmEngine, String repoUrl, String credentialsId)
// ScmConnector.DescriptorImpl has:
//   doFillCredentialsIdItems(...)
//   @POST doTestConnection(repoUrl, credentialsId) — runs `git ls-remote -h <repoUrl>`

// DbConnector.java
@DataBoundConstructor
public DbConnector(String id, String name, String description,
                   String dbEngine, String dbUrl, String port, String username, Secret password)
// DbConnector.DescriptorImpl has:
//   @POST doTestConnection(dbUrl, port, username, password)
```

---

## Storage

### Global
- `GlobalConnectorStorage` — `@Extension`, singleton, serializes to `JENKINS_HOME/connectors.xml`
- Methods: `addConnector(c)`, `removeConnector(id)`, `updateConnector(c)`, `getConnectors()`

### Folder-level
- `FolderConnectorProperty extends AbstractFolderProperty<AbstractFolder<?>>`
- Stored inside the folder's `config.xml` via `owner.save()`
- Methods: same as Global

### Resolver (read-only lookup)
```java
// ConnectorResolver.getAllConnectors(Item context)
//   1. Adds all GlobalConnectorStorage connectors
//   2. Walks item.getParent() chain upward, collecting FolderConnectorProperty connectors
//   Returns merged list (global first, then folder-scoped)

// ConnectorResolver.getConnector(String id, Item context)
//   Walks folder chain first, then falls back to global (folder-nearest wins)
```

---

## ConnectorManagementAction (Main Controller)

```java
public class ConnectorManagementAction implements Action {
    private final Object context;  // Jenkins (global) or AbstractFolder (folder-scoped)

    // Constructor variants:
    public ConnectorManagementAction()                         // global
    public ConnectorManagementAction(AbstractFolder<?> folder) // folder-scoped

    // Jelly helpers:
    public boolean isFolder()          // used in index.jelly instead of instanceof
    public String getScopeLabel()      // human-readable storage location string
    public Connector getConnector(String id)
    public List<Connector> getConnectors()
    public ExtensionList<ConnectorDescriptor> getConnectorDescriptors()

    // Stapler routes (form action="X" → doX()):
    @POST doCreate(req, rsp)   // form action="create"
    @POST doUpdate(req, rsp)   // form action="update"
    @POST doDelete(id, req, rsp) // form action="delete"  ← NOTE: NOT "doDelete"

    // JSON parsing helper (shared by doCreate + doUpdate):
    private Connector parseConnectorFromRequest(req, form)
    // — reads stapler-class from connector sub-object
    // — validates id: non-empty, matches [\w\-\.]+, no spaces
    // — hoists id/name/description from top-level form into connector sub-object
    // — flattens JSONArrays (Jenkins Table→Div migration bug workaround)
    // — calls descriptor.newInstance(req, connectorPayload)
}
```

**Stapler convention**: Form `action="create"` → `doCreate()`, `action="delete"` → `doDelete()`, `action="update"` → `doUpdate()`. Do NOT prefix with "do" in the form action attribute.

---

## ConnectorFolderActionFactory

```java
@Extension
public class ConnectorFolderActionFactory extends TransientActionFactory<AbstractFolder> {
    // Attaches ConnectorManagementAction to every AbstractFolder
    // → makes /job/MyFolder/connectors/* accessible
}
```

**IMPORTANT**: `WorkflowJob` (Pipeline) is NOT an `AbstractFolder` and does NOT get `/connectors/dialog`. When a Pipeline job is inside a folder, use the **parent folder's** URL: `job/MyFolder/connectors/dialog`, not `job/MyFolder/job/MyPipeline/connectors/dialog`.

---

## UI / Jelly Pages

| File | Purpose |
|------|---------|
| `ConnectorManagementAction/index.jelly` | Dashboard listing all connectors with Edit/Delete |
| `ConnectorManagementAction/new.jelly` | Full-page create form |
| `ConnectorManagementAction/edit.jelly` | Full-page edit form (pre-populates via `instance` var) |
| `ConnectorManagementAction/dialog.jelly` | Minimal frameless create form for inline popup |
| `ConnectorParameterDefinition/index.jelly` | Build parameter UI: select existing + Add button |
| `ConnectorParameterDefinition/config.jelly` | Job config UI for the parameter itself |
| `domain/ScmConnector/config.jelly` | SCM-specific fields (card grid + repoUrl + creds) |
| `domain/DbConnector/config.jelly` | DB-specific fields |

### Key Jelly Patterns

**`instance` variable** — Setting `<j:set var="instance" value="${connector}"/>` in a Jelly page makes sub-forms (via `f:dropdownDescriptorSelector`) automatically pre-populate their `f:textbox field="repoUrl"` etc. via `${instance.repoUrl}`.

**`f:dropdownDescriptorSelector`** — No native pre-selection support. In `edit.jelly`, JavaScript auto-selects the correct tab after page load by matching `select option text === instance.descriptor.displayName`.

**Inline dialog** — Uses native HTML5 `<dialog>` element with `::backdrop { blur }`. An `<iframe>` loads `dialog.jelly`. After successful save, the iframe calls `window.parent.closeConnectorDialogAndRefresh()` to close the modal and refresh the connector `<select>` via AJAX.

**Delete form** — `<f:form action="delete?id=${c.id}">` (NOT `doDelete`).

---

## ConnectorParameterDefinition

```java
// Build parameter: lets users pick a connector when triggering a build
public String getFolderDialogUrl(StaplerRequest req)
// — Finds ancestor Item from req, walks parent chain to first AbstractFolder
// — Returns "<rootUrl>/<folderUrl>/connectors/dialog" or null if no folder ancestor
// — Called in index.jelly as ${it.getFolderDialogUrl(request)} to embed server-side

// DescriptorImpl.doFillValueItems(@AncestorInPath Item item)
// — Permission: Item.READ (not ADMINISTER)
// — Returns "-- Select Connector --" + ConnectorResolver.getAllConnectors(item)
// — Label format: "[ScmType] Name (id)"
```

---

## Pipeline Step

```groovy
withConnector('my-github-connector') {
    sh "git clone $CONNECTOR_URL"
}
// Injects env vars: CONNECTOR_URL, CONNECTOR_CREDENTIALS_ID (SCM)
//                   CONNECTOR_URL, CONNECTOR_PORT, CONNECTOR_USER, CONNECTOR_PASSWORD (DB)
```

---

## Permissions

```java
// ConnectorPermissions.java — uses Item.PERMISSIONS group + PermissionScope.ITEM_GROUP
// Valid IDs (Java identifier-safe): ConnectorView, ConnectorCreate, ConnectorUpdate, ConnectorDelete
public static final Permission VIEW   = new Permission(Item.PERMISSIONS, "ConnectorView", ...)
public static final Permission CREATE = new Permission(Item.PERMISSIONS, "ConnectorCreate", ...)
public static final Permission UPDATE = new Permission(Item.PERMISSIONS, "ConnectorUpdate", ...)
public static final Permission DELETE = new Permission(Item.PERMISSIONS, "ConnectorDelete", ...)
```

---

## Known Issues / Gotchas

1. **Jenkins Table→Div migration bug**: `f:dropdownDescriptorSelector` can produce duplicate `connectorType/stapler-class/$class` as JSONArrays. `parseConnectorFromRequest` flattens these by taking `array.getString(array.size() - 1)`.

2. **WorkflowJob has no /connectors**: Project scope must point to **parent folder**, not the job itself. `ConnectorParameterDefinition.getFolderDialogUrl()` handles this server-side.

3. **`instanceof` in Jelly EL**: Not supported. Use Java helper methods (`isFolder()`, `getScopeLabel()`) instead.

4. **doDelete naming**: Jelly form `action="delete"` → Java `doDelete()`. Using `action="doDelete"` causes 404.

5. **Connector ID rules**: Must match `[\w\-\.]+` — no spaces, no special chars. Validated both client-side (oninput JS) and server-side in `parseConnectorFromRequest`.

6. **`f:dropdownDescriptorSelector` edit pre-selection**: No native default= support. Use JS after page load: find `<select>` options, match by `displayName`, set `selectedIndex`, dispatch `change` event.

---

## Dependencies (pom.xml)

- `cloudbees-folder` plugin — for `AbstractFolder`, `AbstractFolderProperty`
- `workflow-step-api` — for `Step`, `StepExecution`
- `credentials` plugin — for credentials dropdown in ScmConnector
- `credentials-binding` — optional, for credential injection
