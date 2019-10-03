// Copyright 2018-present 650 Industries. All rights reserved.

#if __has_include(<EXErrorRecovery/EXErrorRecoveryModule.h>)
#import "EXScopedErrorRecoveryModule.h"

@interface EXScopedErrorRecoveryModule ()

@property (nonatomic, strong) NSString *experienceId;

@end

@implementation EXScopedErrorRecoveryModule

- (instancetype)initWithExperienceId:(NSString *)experienceId
{
  if (self = [super init]) {
    _experienceId = experienceId;
  }
  return self;
}

- (BOOL)setRecoveryProps:(NSString *)props
{
  NSUserDefaults *preferences = [NSUserDefaults standardUserDefaults];
  NSDictionary *errorRecoveryStore = [preferences objectForKey:[self userDefaultsKey]];
  if (!errorRecoveryStore) {
    return [EXScopedErrorRecoveryModule updateUserDefaults:preferences
                                    withErrorRecoveryStore:@{ _experienceId: props }
                                                   withKey:[self userDefaultsKey]];
  } else {
    NSMutableDictionary *propsToSave = [errorRecoveryStore mutableCopy];
    [propsToSave setObject:props forKey:_experienceId];
    return [EXScopedErrorRecoveryModule updateUserDefaults:preferences
                                    withErrorRecoveryStore:propsToSave
                                                   withKey:[self userDefaultsKey]];
  }
}

- (NSString *)consumeRecoveryProps
{
  NSUserDefaults *preferences = [NSUserDefaults standardUserDefaults];
  NSDictionary *errorRecoveryStore = [preferences objectForKey:[self userDefaultsKey]];
  if (errorRecoveryStore) {
    NSString *props = [errorRecoveryStore objectForKey:_experienceId];
    if (props) {
      NSMutableDictionary *storeWithRemovedProps = [errorRecoveryStore mutableCopy];
      [storeWithRemovedProps removeObjectForKey:_experienceId];
      
      [EXScopedErrorRecoveryModule updateUserDefaults:preferences
                               withErrorRecoveryStore:storeWithRemovedProps
                                              withKey:[self userDefaultsKey]];
      return props;
    }
  }
  return nil;
}

+ (BOOL)updateUserDefaults:(NSUserDefaults *)preferences
    withErrorRecoveryStore:(NSDictionary *)newStore
                   withKey:(NSString *)key
{
  [preferences setObject:newStore forKey:key];
  return [preferences synchronize];
}

- (NSString *)userDefaultsKey
{
   // We don't want to recovery props propagate from one version to another.
   // `EX` will be changed to `ABI_X_X` during versioning.
  return [NSString stringWithFormat:@"EX.%@", [super userDefaultsKey]];
}
@end
#endif
