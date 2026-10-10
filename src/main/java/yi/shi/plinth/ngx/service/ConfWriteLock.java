package yi.shi.plinth.ngx.service;

import com.google.inject.Singleton;

/**
 * 配置目录文件写入互斥锁（单管理员语义下，把"候选校验暂改文件 / 保存 / 证书替换"
 * 等会临时改动磁盘的操作串行化，避免交叉还原）。
 */
@Singleton
public class ConfWriteLock {

    public final Object monitor = new Object();
}
