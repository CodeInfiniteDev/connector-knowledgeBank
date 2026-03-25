# MidPoint 配置对象的 CI/CD 持续部署实践

## 核心思路

将 MidPoint 配置对象（Resource、Role、Object Template 等）以 XML 文件存入 Git，通过 CI/CD 自动部署到 SIT/UAT/PROD。

关键点：**XML 文件在所有环境完全一样**，环境差异通过 MidPoint 原生机制处理，不需要脚本替换。

---

## 一、环境差异怎么处理？

MidPoint Studio 的 `$(key)` 占位符只是 Studio 插件在上传前做的客户端替换，MidPoint 服务端不认识这个语法。CI/CD 中不应该用这种方式。

MidPoint 官方提供了两个原生机制：

### 1. Constants（处理非敏感配置：地址、端口、DN 等）

每个环境的 MidPoint 实例在自己的 `$MIDPOINT_HOME/config.xml` 中定义 constants：

**SIT 环境的 config.xml：**
```xml
<configuration>
    <midpoint>
        <repository>...</repository>
        <constants>
            <ldapHost>ldap-sit.example.com</ldapHost>
            <ldapPort>389</ldapPort>
            <ldapBaseContext>dc=sit,dc=example,dc=com</ldapBaseContext>
            <ldapBindDn>cn=admin,dc=sit,dc=example,dc=com</ldapBindDn>
        </constants>
    </midpoint>
</configuration>
```

**PROD 环境的 config.xml：**
```xml
<configuration>
    <midpoint>
        <repository>...</repository>
        <constants>
            <ldapHost>ldap.example.com</ldapHost>
            <ldapPort>636</ldapPort>
            <ldapBaseContext>dc=example,dc=com</ldapBaseContext>
            <ldapBindDn>cn=midpoint,dc=example,dc=com</ldapBindDn>
        </constants>
    </midpoint>
</configuration>
```

也可以不改 config.xml，用环境变量设置（适合容器化部署）：
```bash
MP_SET_midpoint_constants_ldapHost=ldap.example.com
MP_SET_midpoint_constants_ldapPort=636
MP_SET_midpoint_constants_ldapBaseContext=dc=example,dc=com
```

或 JVM 参数：
```bash
-Dmidpoint.constants.ldapHost=ldap.example.com
```

**XML 配置对象中这样引用（所有环境用同一份文件）：**
```xml
<resource oid="b26554d2-41fc-11e5-a652-3c970e44b9e2">
    <name>Corporate LDAP</name>
    <connectorConfiguration>
        <configurationProperties>
            <ldap:host>
                <expression><const>ldapHost</const></expression>
            </ldap:host>
            <ldap:port>
                <expression><const>ldapPort</const></expression>
            </ldap:port>
            <ldap:baseContext>
                <expression><const>ldapBaseContext</const></expression>
            </ldap:baseContext>
            <ldap:bindDn>
                <expression><const>ldapBindDn</const></expression>
            </ldap:bindDn>
        </configurationProperties>
    </connectorConfiguration>
</resource>
```

在 Groovy 表达式中也可以用：
```xml
<expression>
    <script>
        <code>'uid=' + name + ',' + midpoint.getConst('ldapBaseContext')</code>
    </script>
</expression>
```

> **注意**：Constants 在 MidPoint 启动时加载一次，修改后需要重启才生效。

### 2. Secrets Providers（处理敏感配置：密码、密钥等）

从 MidPoint 4.8 开始，密码等敏感信息用 Secrets Providers，MidPoint 运行时从环境变量/文件/Docker Secret 读取。

**System Configuration 中配置 provider（这个也存在 Git 中）：**
```xml
<systemConfiguration>
    <secretsProviders>
        <environmentVariables>
            <identifier>env-provider</identifier>
            <allowKeyPrefix>MP_SECRET_</allowKeyPrefix>
        </environmentVariables>
    </secretsProviders>
</systemConfiguration>
```

**XML 配置对象中引用：**
```xml
<ldap:bindPassword>
    <value>
        <t:externalData>
            <t:provider>env-provider</t:provider>
            <t:key>LDAP_BIND_PASSWORD</t:key>
        </t:externalData>
    </value>
</ldap:bindPassword>
```

各环境只需设置对应的环境变量：
- SIT: `MP_SECRET_LDAP_BIND_PASSWORD=sit-password`
- PROD: `MP_SECRET_LDAP_BIND_PASSWORD=prod-secure-password`

### 总结：XML 中的变量用法

| 场景 | 机制 | XML 写法 |
|------|------|---------|
| 地址、端口、DN 等 | Constants (`config.xml`) | `<expression><const>ldapHost</const></expression>` |
| 密码、密钥 | Secrets Providers | `<t:externalData><t:provider>...</t:provider><t:key>...</t:key></t:externalData>` |
| 表达式中用常量 | Constants + Groovy | `midpoint.getConst('ldapBaseContext')` |

---

## 二、怎么部署？（不需要 REST API 或 Ninja）

### 最官方的方式：post-initial-objects

MidPoint 自带 `post-initial-objects` 机制。把 XML 文件放到 `$MIDPOINT_HOME/post-initial-objects/` 目录下，MidPoint 启动时自动导入。

- 文件名以三位数字开头，决定导入顺序（全局排序）
- 已存在的对象会被覆盖
- 导入后文件名加 `.done` 后缀，下次启动不会重复导入
- 支持子目录

```
$MIDPOINT_HOME/post-initial-objects/
├── resources/
│   └── 030-ldap-resource.xml
├── roles/
│   ├── 040-role-employee.xml
│   └── 050-role-contractor.xml
├── templates/
│   └── 020-user-template.xml
└── system/
    └── 010-system-configuration.xml
```

部署顺序建议：
1. `010-` System Configuration（含 Secrets Providers 配置）
2. `020-` Object Templates
3. `030-` Resources
4. `040-` Roles
5. `050-` Orgs
6. `060-` Tasks

### 部署触发方式

**方式 A：重启 MidPoint（最简单）**

CI/CD 把文件复制到 `post-initial-objects/`，然后重启 MidPoint。适合容器化部署（重建 Pod）。

**方式 B：REST API（不需要重启）**

如果不想重启，可以用 REST API 直接上传对象。适合频繁部署的场景。

```bash
curl -X PUT \
  "https://midpoint.example.com/midpoint/ws/rest/resources/b26554d2-41fc-11e5-a652-3c970e44b9e2" \
  -H "Content-Type: application/xml" \
  -u "administrator:password" \
  -d @objects/resources/030-ldap-resource.xml
```

**方式 C：Ninja（直接操作数据库，不需要 MidPoint 运行）**

```bash
./bin/ninja.sh import -O -i /path/to/objects/
```

适合维护、迁移场景，不推荐作为常规 CI/CD 方式（绕过授权和审计）。

---

## 三、完整 CI/CD 架构

### Git 仓库结构

```
midpoint-config/
├── objects/                          # 所有环境共用的 XML 配置
│   ├── system/
│   │   └── 010-system-configuration.xml
│   ├── templates/
│   │   └── 020-user-template.xml
│   ├── resources/
│   │   └── 030-ldap-resource.xml
│   ├── roles/
│   │   ├── 040-role-employee.xml
│   │   └── 041-role-contractor.xml
│   └── tasks/
│       └── 060-recon-task.xml
├── deploy.sh                         # 部署脚本
└── .gitlab-ci.yml
```

注意：**不需要** environments 目录或 properties 文件，因为环境差异全在各环境的 MidPoint 实例自己的 config.xml / 环境变量中。

### 部署脚本（deploy.sh）

```bash
#!/bin/bash
# 用法: ./deploy.sh <midpoint_url> <username> <password>
# 通过 REST API 部署所有配置对象（不需要重启）

MP_URL=$1
MP_USER=$2
MP_PASS=$3

get_endpoint() {
  local file=$1
  if grep -q "<resource " "$file"; then echo "resources"
  elif grep -q "<role " "$file"; then echo "roles"
  elif grep -q "<org " "$file"; then echo "orgs"
  elif grep -q "<objectTemplate " "$file"; then echo "objectTemplates"
  elif grep -q "<systemConfiguration " "$file"; then echo "systemConfigurations"
  elif grep -q "<archetype " "$file"; then echo "archetypes"
  elif grep -q "<task " "$file"; then echo "tasks"
  elif grep -q "<securityPolicy " "$file"; then echo "securityPolicies"
  elif grep -q "<service " "$file"; then echo "services"
  else echo ""
  fi
}

get_oid() {
  grep -oP 'oid="[^"]*"' "$1" | head -1 | grep -oP '"[^"]*"' | tr -d '"'
}

FAIL=0
for xml_file in $(find objects/ -name "*.xml" | sort); do
  endpoint=$(get_endpoint "$xml_file")
  oid=$(get_oid "$xml_file")

  if [ -z "$endpoint" ] || [ -z "$oid" ]; then
    echo "SKIP: $xml_file (无法识别类型或缺少 OID)"
    continue
  fi

  echo -n "部署 $xml_file -> $endpoint/$oid ... "

  HTTP_CODE=$(curl -s -o /tmp/mp_resp.txt -w "%{http_code}" \
    -X PUT "${MP_URL}/ws/rest/${endpoint}/${oid}" \
    -H "Content-Type: application/xml" \
    -u "${MP_USER}:${MP_PASS}" \
    -d @"$xml_file")

  if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
    echo "OK ($HTTP_CODE)"
  elif [ "$HTTP_CODE" = "404" ]; then
    # 对象不存在，POST 创建
    HTTP_CODE=$(curl -s -o /tmp/mp_resp.txt -w "%{http_code}" \
      -X POST "${MP_URL}/ws/rest/${endpoint}" \
      -H "Content-Type: application/xml" \
      -u "${MP_USER}:${MP_PASS}" \
      -d @"$xml_file")
    if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
      echo "CREATED ($HTTP_CODE)"
    else
      echo "FAIL ($HTTP_CODE)"
      FAIL=1
    fi
  else
    echo "FAIL ($HTTP_CODE)"
    FAIL=1
  fi
done

exit $FAIL
```

### GitLab CI（.gitlab-ci.yml）

```yaml
stages:
  - validate
  - deploy-sit
  - deploy-uat
  - deploy-prod

validate:
  stage: validate
  script:
    - for f in $(find objects/ -name "*.xml"); do xmllint --noout "$f" || exit 1; done

deploy-sit:
  stage: deploy-sit
  script:
    - ./deploy.sh "$MIDPOINT_SIT_URL" "$SIT_USER" "$SIT_PASS"
  only: [main]
  environment: sit

deploy-uat:
  stage: deploy-uat
  script:
    - ./deploy.sh "$MIDPOINT_UAT_URL" "$UAT_USER" "$UAT_PASS"
  only: [main]
  when: manual
  environment: uat

deploy-prod:
  stage: deploy-prod
  script:
    - ./deploy.sh "$MIDPOINT_PROD_URL" "$PROD_USER" "$PROD_PASS"
  only: [tags]
  when: manual
  environment: production
```

GitLab CI/CD Variables 中配置各环境的 URL 和凭据（标记为 Protected + Masked）。

---

## 四、日常开发工作流

```
开发者 (MidPoint Studio)
    │
    ├── 1. 在 DEV 环境用 Studio 开发/修改配置
    ├── 2. 用 Studio 导出 XML（或直接在 IDE 编辑 XML）
    ├── 3. 确保 XML 中用 <const> 和 Secrets Provider 而不是硬编码值
    ├── 4. 提交到 GitLab，发起 Merge Request
    ├── 5. Code Review
    ├── 6. 合并到 main → 自动部署 SIT
    ├── 7. 手动触发 → 部署 UAT
    └── 8. 打 Tag + 手动触发 → 部署 PROD
```

### 关键注意事项

1. **每个对象必须有固定 OID** — 用 `uuid` 命令生成，写死在 XML 中。这样才能跨环境幂等部署。

2. **从 Studio 导出时清理 XML** — Studio 的 `Cleanup File` 功能可以去掉 metadata、operationalState 等运行时数据。

3. **不要把 Studio 的 `$(key)` 占位符提交到 Git** — 改用 `<const>` 和 Secrets Provider。Studio 本地开发时可以用 properties 文件，但 Git 中的 XML 应该用 MidPoint 原生语法。

4. **密码绝不入 Git** — 用 Secrets Providers，各环境通过环境变量注入。

---

## 五、容器化部署（Docker/K8s）的推荐方式

如果 MidPoint 跑在容器中，最简单的方式是 post-initial-objects + 环境变量：

```yaml
# docker-compose.yml
services:
  midpoint:
    image: evolveum/midpoint:4.8
    volumes:
      - ./objects:/opt/midpoint/var/post-initial-objects
    environment:
      # Constants
      - MP_SET_midpoint_constants_ldapHost=ldap.example.com
      - MP_SET_midpoint_constants_ldapPort=636
      # Secrets
      - MP_SECRET_LDAP_BIND_PASSWORD=secret
```

Kubernetes 中用 ConfigMap 挂载 XML，用 Secret 注入密码环境变量。

CI/CD 只需要更新 ConfigMap 内容然后滚动重启 Pod。

---

## 六、方式对比

| 方式 | 需要重启？ | 适用场景 | 官方程度 |
|------|-----------|---------|---------|
| post-initial-objects | 是 | 容器化部署、初始化 | 官方内置 |
| REST API | 否 | 频繁部署、不能停机 | 官方接口 |
| Ninja | 否（直接操作 DB） | 维护、迁移、MidPoint 挂了 | 官方工具（但绕过审计） |

**推荐**：容器化用 post-initial-objects，传统部署用 REST API。
