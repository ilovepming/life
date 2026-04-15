--比较当前线程id和redis中的锁id
if(redis.call('get',KEYS[1]) == ARGV[1]) then
redis.call('del',KEYS[1]);
end
return 0;