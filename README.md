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
All operations are available through the Router (port 8000) or directly through individual servers. Note: curl is an alias for Invoke-WebRequest for Windows PowerShell.

### 1. Create Directory (mkdir)

```bash
curl -X POST "http://localhost:8000/mkdir?path=/home"
curl -X POST "http://localhost:8000/mkdir?path=/home/maria"
```

**PowerShell:**
```powershell
Invoke-WebRequest -Uri "http://localhost:8000/mkdir?path=/home" -Method POST
```

### 2. Create File (create)

```bash
curl -X POST "http://localhost:8000/create?path=/home/maria/file1.txt"
curl -X POST "http://localhost:8000/create?path=/home/maria/file2.txt"
```

### 3. List Directory Contents (readdir)

```bash
curl "http://localhost:8000/readdir?path=/home/maria"
```

Expected output:
```
file1.txt, file2.txt
```

### 4. Get File/Directory Metadata (stat)

```bash
curl "http://localhost:8000/stat?path=/home/maria/file1.txt"
```

Expected output:
```
Path: /home/maria/file1.txt, Type: file, Parent: /home/maria, Timestamp: 1234567890
```

### 5. Remove File (rm)

```bash
curl -X POST "http://localhost:8000/rm?path=/home/maria/file1.txt"
```

### 6. Remove Empty Directory

```bash
curl -X POST "http://localhost:8000/rm?path=/home/maria"
```

### 7. Show Cluster Status

```bash
curl "http://localhost:8000/cluster"
```

### 8. Dump Server Metadata

Check which metadata is stored on each server:

```bash
# Server 1
curl "http://localhost:8081/dump"

# Server 2
curl "http://localhost:8082/dump"

# Server 3
curl "http://localhost:8083/dump"
```

## Test Scenario
```bash
# Create directory hierarchy
curl -X POST "http://localhost:8000/mkdir?path=/home"
curl -X POST "http://localhost:8000/mkdir?path=/home/user1"
curl -X POST "http://localhost:8000/mkdir?path=/home/user2"

# Create files in different directories
curl -X POST "http://localhost:8000/create?path=/home/user1/file1.txt"
curl -X POST "http://localhost:8000/create?path=/home/user1/file2.txt"
curl -X POST "http://localhost:8000/create?path=/home/user2/file3.txt"
curl -X POST "http://localhost:8000/create?path=/home/user2/file4.txt"

# List directory contents
curl "http://localhost:8000/readdir?path=/home"
curl "http://localhost:8000/readdir?path=/home/user1"
curl "http://localhost:8000/readdir?path=/home/user2"

# Get file metadata
curl "http://localhost:8000/stat?path=/home/user1/file1.txt"

# Show distribution across servers
curl "http://localhost:8081/dump"
curl "http://localhost:8082/dump"
curl "http://localhost:8083/dump"

# Clean up
curl -X POST "http://localhost:8000/rm?path=/home/user1/file1.txt"
curl -X POST "http://localhost:8000/rm?path=/home/user2"
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