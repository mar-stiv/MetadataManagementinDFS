# Project 4: Metadata Management in Distributed File System (DFS)

## Overview
Our project implements a metadata management module for a Distributed File System. It simulates how modern DFSs (such as HDFS, Ceph, etc) organise and distribute metadata across multiple metadata servers.

## Architecture
The system consists of:
- **1 Router Gateway**: Routes client requests to the appropriate metadata server based on path hashing
- **3 Metadata Servers**: Store and manage distributed metadata for file system operations

```
Client → Router (port 8000) → Metadata Servers (ports 8081-8083)
                                        ↓
                                Hash-based Distribution
```

## How to Run

## 1. Prerequisites
- Docker and Docker Compose installed
- Java 

## 2. Build and Run

```bash
# Build and start all containers
docker compose up --build
```

This will start:
- Router at `http://localhost:8000`
- Metadata Server 1 at `http://localhost:8081`
- Metadata Server 2 at `http://localhost:8082`
- Metadata Server 3 at `http://localhost:8083`

## 3. Calling API endpoints
All operations are available through the Router (port 8000) or directly through individual servers. You may test the following commands using CMD or Command Prompt in IntelliJ IDE.

### 1. Create Directory (mkdir)

```bash
$ mkdir home
$ mkdir home/maria
```

### 2. Create File (touch)

```bash
$ touch home/maria/file1.txt
$ touch home/maria/file2.txt
```

### 3. List Directory Contents (readdir)

```bash
$ readdir home/maria
```

Expected output:
```
file1.txt, file2.txt
```

### 4. Get File/Directory Metadata (stat)

```bash
$ stat home/maria/file1.txt
```

Expected output:
```
Path: /home/maria/file1.txt, Type: file, Parent: /home/maria, Timestamp: 1234567890
```

### 5. Remove File (rm)

```bash
$ rm home/maria/file1.txt
```

### 6. Remove Empty Directory

```bash
$ rm home/maria
```

### 7. Show Cluster Status

```bash
$ cluster
```

### 8. Dump Server Metadata

Check which metadata is stored on each server:

```bash
# Server 1
$ dump 1

# Server 2
$ dump 2

# Server 3
$ dump 2
```

### 9. Show Tree of a Specific Folder
Example for ```/home``` folder.

Showing file and folder names:
```bash
$ tree home  
```
Showing absolute paths of files and folders:
```bash
$ fulltree home
```

## Test Scenario
```bash
# Create directory hierarchy
$ mkdir home
$ mkdir home/user1
$ mkdir home/user2

# Create files in different directories
$ touch home/user1/file1.txt
$ touch home/user1/file2.txt
$ touch home/user2/file3.txt
$ touch home/user2/file4.txt

# List directory contents
$ readdir home
$ readdir home/user1
$ readdir home/user2

# Get file metadata
$ stat home/user1/file1.txt

# Show distribution across servers
$ dump 1
$ dump 2
$ dump 3


# Show trees
$ tree home
$ fulltree home 

# Clean up
rm home/user1/file1.txt
rm home/user2
```

## Functionality and Features

### 1. Hash-based Distribution
The router distributed metadata across servers via a hash function presented below. Each path is deterministically assigned to one of the three servers. Different paths are distributed across different servers based on their hash values.
```
server_index = abs(hash(path)) % num_servers
```

### 2. Metadata Structure
Each metadata entry contains:
- **Path**: Full file/directory path
- **Type**: "file" or "dir"
- **Parent**: Parent directory path (null for root)
- **Timestamp**: Creation timestamp

### Routing Flow
1. Client sends request to Router
2. Router extracts the path and calculates `hash(path) % 3`
3. Router forwards request to the selected metadata server
4. Metadata server performs the operation and returns response
5. Router returns response to client

### Environment Variables
The system uses these environment variables:
- `MODE`: `router` or `server`
- `SERVER_ID`: Server identifier (1, 2, 3...)
- `PORT`: HTTP port number
- `SERVERS`: Comma-separated list of backend URLs (router only)

## Directory Structure
```
.
├── MetadataServer.java      # Handles metadata operations and storage
├── RouterGateway.java        # Hash-based request routing
├── Main.java                 # Entrypoint (router/server mode)
├── Dockerfile                # Container build instructions
├── docker-compose.yml        # Orchestrates 1 router + 3 servers
└── README.md                 # This file
```

## TODO
Test: tree command for the root directory
