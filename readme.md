# 日志生命周期

1. leader接收客户端请求
2. leader向follower复制日志
3. leader判断多数复制后将日志状态改为已提交
4. leader更新commitIndex后，后续发送的AppendEntries RPC会携带最新的leaderCommit同步给所有Follower
5. 所有节点定期检查commitIndex > lastApplied，应用日志

# 服务器角色

* leader
    * 属性：
        * commitIndex：已知已提交的日志条目中最大索引
        * lastApplied：已应用到状态机的日志条目中的最大索引
        * currentTerm：当前服务器感知到的最新term
        * votedFor：当前term内该服务器投票的candidateID
        * log：日志数组，每个条目包含状态机待执行的命令以及该条目被leader接收时的term
        * nextIndex[]：针对每个服务器记录下一个待发送给该服务器的日志条目
        * matchIndex：针对每个服务器，记录已知已复制到该服务器的日志条目中的最大索引
    * 方法
* follower
    * 属性：
        * commitIndex：已知已提交的日志条目中最大索引
        * lastApplied：已应用到状态机的日志条目中的最大索引
        * currentTerm：当前服务器感知到的最新term
        * votedFor：当前term内该服务器投票的candidateID
        * log：日志数组，每个条目包含状态机待执行的命令以及该条目被leader接收时的term
    * 方法
* candidate
    * 属性：
        * commitIndex：已知已提交的日志条目中最大索引
        * lastApplied：已应用到状态机的日志条目中的最大索引
        * currentTerm：当前服务器感知到的最新term
        * votedFor：当前term内该服务器投票的candidateID
        * log：日志数组，每个条目包含状态机待执行的命令以及该条目被leader接收时的term
    * 方法

# 服务器行为

* 通用行为：
    * 执行已提交未应用到状态机的日志
    * 若收到的RPC中任期号T大于本地任期号，则更新任期号并转为Follower
* candidate：
    * 请求投票：广播`RequestVote RPC`以请求投票（需要保存有哪些服务器）
    * 若收到新leader的`AppendEntries RPC`则转化为follower
    * 选举超时重新发起选举
* leader：
    * 定期向follower发送心跳（需要保存有哪些leader）
    * 收到client命令后，将新条目追加到本地日志，待条目被应用到状态机后向客户端返回执行结果
    * 与follower进行日志同步
* follower：
    * 响应来自leader和candidate的RPC请求
    * 若在选举超时时间内未收到当前leader的`AppendEntries RPC`，也未向任何candidate授予选票，则切换为candidate

# 状态转换

* candidate -> leader：发起选举获得多数选票
* candidate -> follower：选举时收到了超过半数的`RequestVote RPC`
* leader -> follower：任期内收到比自己任期号更大的RPC
* follower -> candidate：选举超时时间内未收到`AppendEntries RPC`

# RPC

## AppendEntries RPC

### 心跳(矫正参数)

leader -> follower：发送空的`AppendEntries RPC`

心跳

leader->follower

|      字段      |   值   |          说明           |
|:------------:|:-----:|:---------------------:|
|     term     |   3   |     leader当前是第3任期     |
|   leaderId   | node1 |       leader的ID       |
| preLogIndex  |  10   |      已经同步的日志到了10      |
|  preLogTerm  |   3   |     已经同步到的日志任期未3      |
|   entries    |  []   |       心跳不包含日志实体       |
| leaderCommit |   1   | Leader已经将index=1的日志提交 |

follower收到后

1. 检查term=3>=自己的currentTerm -> OK
2. 检查preLogIndex=10与preLogTerm=3是否匹配
3. 没有要追加的日志，跳过
4. 检查leaderCommit=1，如果Follower的commitIndex<1则尝试更新
5. 返回响应（包含term和success）

### 日志复制

* leader -> follower：leader将自己新追加的日志条目通过`AppendEntries RPC`发送给follower
* follower -> leader：follower收到后如果匹配成功则写入自己的日志存储中，如果匹配失败

leader->follower

|      字段      |                值                |          说明           |
|:------------:|:-------------------------------:|:---------------------:|
|     term     |                3                |     leader当前是第3任期     |
|   leaderId   |              node1              |       leader的ID       |
| preLogIndex  |               10                |      已经同步的日志到了10      |
|  preLogTerm  |                3                |     已经同步到的日志任期未3      |
|   entries    | [LogEntity(index,term,command)] |       心跳不包含日志实体       |
| leaderCommit |                1                | Leader已经将index=1的日志提交 |

follower收到后

1. 检查term=3>=自己的currentTerm -> OK
2. 检查preLogIndex=10与preLogTerm=3是否匹配
3. 日志一致，可以安全追加
4. 检查leaderCommit=1，如果Follower的commitIndex<1则尝试更新
5. 返回响应（包含term和success）

如果日志不匹配，follower会拒绝，返回响应，此时leader会降低nextIndex[follower]然后重发RPC，直到日志完全同步

### RequestVote RPC

candidate -> follower

|      字段      |   值   |               说明                |
|:------------:|:-----:|:-------------------------------:|
|     term     |   3   | candidate当前是第3任期(上一个leader周期+1) |
| candidateId  | node1 |          candidate的ID           |
| lastLogIndex |  10   |       candidate最后日志的索引为10       |
| lastLogTerm  |   3   |       candidate最后日志的任期为3        |

follower收到RPC后：

1. 校验term，如果term<currentTerm则返回false
2. 若接收方的votedFor未null或等于candidateId，且候选者的日志至少与接收方一样新则返回true
3. 当follower投票之后（设置votedFor=true），如果收到了其他candidate的投票请求，则返回false
4. 当follower收到心跳后将votedFor=false

# 配置文件

* 节点自身配置：
    * node_id：当前节点的唯一标识
    * host：当前节点监听的IP地址
    * port：节点监听的端口号
    * http_port：与客户端交互
* 集群中其他节点的信息：
    * members：列表，表明集群中有哪些节点，每个member包含id,host,port
* Raft参数
    * election_timeout_min：最小选举超时时间
    * election_timeout_out：最大选举超时时间
    * heartbeat_interval：心跳间隔时间
    * rpc_timeout：RPC超时时间
* 存储：
    * raft_log_dir：raft日志存储磁盘路径

```yaml
# 当前节点信息
node_id:              node1
host:                 127.0.0.1
port:                 8080
storage_path:         ./data/node1

# 集群其他节点（Peers）
peers:
    -   node_id: node2
        host:    127.0.0.1
        port:    8081
    -   node_id: node3
        host:    127.0.0.1
        port:    8082

# Raft 行为参数
election_timeout_min: 150
election_timeout_max: 300
heartbeat_interval:   100
```

# 目录结构

```text
├── raft/                            # Raft核心实现包
│   ├── RaftNode.java               # Raft节点主类
│   ├── RaftConfig.java             # Raft配置类
│   ├── LogEntry.java               # 日志条目类
│   ├── state/                      # 状态相关类
│   │   ├── NodeState.java         # 节点状态接口
│   │   ├── FollowerState.java     # Follower状态实现
│   │   ├── CandidateState.java    # Candidate状态实现
│   │   └── LeaderState.java       # Leader状态实现
│   ├── rpc/                        # RPC相关类
│   │   ├── RpcClient.java         # RPC客户端
│   │   ├── RpcServer.java         # RPC服务端
│   │   ├── raft.rpc.AppendEntriesRequest.java   # 追加条目请求
│   │   ├── raft.rpc.AppendEntriesResponse.java  # 追加条目响应
│   │   ├── raft.rpc.RequestVoteRequest.java     # 请求投票请求
│   │   └── raft.rpc.RequestVoteResponse.java    # 请求投票响应
│   ├── log/                        # 日志管理相关类
│   │   ├── LogManager.java        # 日志管理器
│   │   ├── LogStorage.java        # 日志存储接口
│   │   └── FileLogStorage.java    # 基于文件的日志存储实现
│   ├── persistence/                # 持久化相关类
│   │   ├── StateStorage.java      # 状态存储接口
│   │   └── FileStateStorage.java  # 基于文件的状态存储实现
│   └── util/                       # 工具类
│       └── TimerUtil.java         # 定时器工具类
├── server/                         # 服务端应用
│   └── RaftServer.java            # Raft服务端主类
└── client/                         # 客户端应用
    └── RaftClient.java            # Raft客户端主类
```