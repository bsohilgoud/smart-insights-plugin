## TODOs
---
- Identify any tools dependency is missing 
    - then its appfactory related
    - Also check for `AppFactoryExceptions` - Then highlight those errors to the users
    - If path has `visualizer`
        - Focus on the `CI Build` (logs) - check for the final error provided the ci-build ()
            - Android - Signing
            - iOS iPA
            - Web Fabric Publish or services
        - Try to go deeper i mean `nested job analysis` (custom build config / custom hooks)


- Provide the links to the basecamp maybe it can check the ci-build failures
- dont hallucinate
- Improve the UI (Modern)


## Common Issues:
---
0. Invalid or Missing Parameters 
1. Checkout issues (invalid repo, creds, vpn, branch, timeout, lfs)
2. Visualizer project path - projectProps not found, (PROJECT_PATH)
3. Tools and dependencies 
    - java
    - nodejs
    - maven etc
4. Custom Hooks / Custom Build Config
5. Visualizer
    - CI Build (failure)
    - Models
    - Fabric Creds Issues
        - VPN issues 
        - App not exists etc
    - D8 issues
6. 