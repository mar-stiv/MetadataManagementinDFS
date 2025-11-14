@echo off
if "%1" == "mkdir" (
    if "%2" == "" (
        echo Usage: dfs mkdir ^<path^>
        exit /b 1
    )
    curl -X POST "http://localhost:8000/mkdir?path=%2"
) else if "%1" == "touch" (
      if "%2" == "" (
          echo Usage: dfs touch ^<path^>
          exit /b 1
      )
      curl -X POST "http://localhost:8000/touch?path=%2"
) else if "%1" == "readdir" (
    if "%2" == "" (
        echo Usage: dfs readdir ^<path^>
        exit /b 1
    )
    curl "http://localhost:8000/readdir?path=%2"
) else if "%1" == "stat" (
    if "%2" == "" (
        echo Usage: dfs stat ^<path^>
        exit /b 1
    )
    curl "http://localhost:8000/stat?path=%2"
) else if "%1" == "rm" (
    if "%2" == "" (
        echo Usage: dfs rm ^<path^>
        exit /b 1
    )
    curl -X POST "http://localhost:8000/rm?path=%2"
) else if "%1" == "cluster" (
    if NOT "%2" == "" (
        echo Usage: dfs cluster
        exit /b 1
    )
    curl "http://localhost:8000/cluster"
) else if "%1" == "dump" (
    if "%2" == "" (
        echo Usage: dfs dump ^<server number^>
        exit /b 1
    )
    curl "http://localhost:808%2/dump"
) else if "%1" == "tree" (
    curl "http://localhost:8000/tree?path=/"
) else if "%1" == "fulltree" (
    curl "http://localhost:8000/fulltree?path=/"
) else (
    echo Usage: dfs ^<command^> [args]
    echo Commands: mkdir, touch, readdir, stat, rm, cluster, dump, tree, fulltree
)
